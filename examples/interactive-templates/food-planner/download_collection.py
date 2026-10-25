#!/usr/bin/env python3
"""Build an offline collection from public publisher sitemaps; cached, paced, resumable."""
import argparse,gzip,hashlib,json,re,time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser
from xml.etree import ElementTree
from import_recipes import import_recipe

AGENT='AgoraOfflineRecipes/0.1'
def locations(raw):
    root=ElementTree.fromstring(raw)
    return [el.text for el in root.iter() if el.tag.endswith('}loc') or el.tag=='loc']
def collect(host,sitemaps,matcher,limit,output,cache):
    origin='https://'+host;robot=RobotFileParser();robot.set_url(origin+'/robots.txt');robot.read();last=0
    def get(url):
        nonlocal last
        if urlparse(url).hostname!=host or not robot.can_fetch(AGENT,url):raise ValueError('URL outside publisher/robots allowance')
        path=cache/(hashlib.sha256(url.encode()).hexdigest()+'.gz')
        if path.exists():return gzip.decompress(path.read_bytes())
        time.sleep(max(0,1-(time.monotonic()-last)));last=time.monotonic()
        with urlopen(Request(url,headers={'User-Agent':AGENT}),timeout=35) as response:
            if urlparse(response.geturl()).hostname!=host:raise ValueError('Unexpected publisher redirect')
            raw=response.read(2000001)
        if len(raw)>2000000:raise ValueError('Publisher page exceeds size limit')
        path.write_bytes(gzip.compress(raw));return raw
    report={'publisher':host,'imported':0,'skipped':[]};urls=[]
    try:
        for sitemap in sitemaps:urls.extend(u for u in locations(get(sitemap)) if re.search(matcher,u))
        # Prefer cooking meals over drinks/desserts for the Ninja subset, without guessing compatibility.
        urls=list(dict.fromkeys(urls))
        if 'sharkninja' in host:
            urls.sort(key=lambda u:(not bool(re.search(r'pressure|curry|risotto|stew|pasta|casserole|soup|chilli|lentil|slow-cook|chicken|beef|potato',u)),u))
        for url in urls:
            if report['imported']>=limit:break
            try:
                raw=get(url);path=cache/'source-{}.html'.format(hashlib.sha256(url.encode()).hexdigest());path.write_bytes(raw)
                try:import_recipe(path,url,output)
                finally:path.unlink(missing_ok=True)
                report['imported']+=1
                if report['imported']%25==0:print(host,report['imported'],flush=True)
            except HTTPError as error:
                if error.code in (401,403,429):raise
                report['skipped'].append({'url':url,'reason':str(error)})
            except (ValueError,TypeError,KeyError) as error:report['skipped'].append({'url':url,'reason':str(error)})
    except Exception as error:report['stopped']=str(error)
    (output/(host+'.report.json')).write_text(json.dumps(report,indent=2));print(json.dumps(report),flush=True)
    return report
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('destination',type=Path);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--publisher',choices=['www.diabetes.org.uk','ninja-recipes.uk','www.sharkninja.co.uk']);args=parser.parse_args();args.destination.mkdir(parents=True,exist_ok=True);args.cache.mkdir(parents=True,exist_ok=True)
    sources=[('www.diabetes.org.uk',['https://www.diabetes.org.uk/sitemap.xml?page='+str(n) for n in range(1,4)],r'/(?:living-with-diabetes|guide-to-diabetes)/recipes/[^/?]+$',560),('www.sharkninja.co.uk',['https://www.sharkninja.co.uk/sitemap_0-product.xml'],r'/REC[^/]+\.html$',300),('ninja-recipes.uk',['https://ninja-recipes.uk/cp_recipe-sitemap.xml'],r'/recipes/[^/]+/$',100)]
    if args.publisher:sources=[source for source in sources if source[0]==args.publisher]
    with ThreadPoolExecutor(max_workers=3) as executor:
        results=list(executor.map(lambda source:collect(*source,args.destination,args.cache),sources))
    (args.destination/'download-report.json').write_text(json.dumps(results,indent=2))
