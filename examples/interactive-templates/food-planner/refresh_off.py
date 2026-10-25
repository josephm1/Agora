#!/usr/bin/env python3
"""Explicit cached OFF v3 barcode lookup; never a Lidl refresh or a name-match assurance."""
import argparse,base64,fcntl,hashlib,json,re,time
from datetime import datetime,timezone
from pathlib import Path
from urllib.request import Request,urlopen

FIELDS='code,product_name,quantity,ingredients_text,ingredients_text_en,allergens_tags,traces_tags,nutriments,nutriscore_grade,nova_group,additives_tags,data_quality_errors_tags'
def lookup(barcode,cache,environment='staging',max_age_hours=720,opener=urlopen,clock=time.time):
    if not re.fullmatch(r'\d{8,14}',barcode):raise ValueError('Enter a package barcode (8–14 digits)')
    if environment not in ('staging','production'):raise ValueError('Invalid OFF environment')
    cache=Path(cache);cache.mkdir(parents=True,exist_ok=True)
    host='world.openfoodfacts.net' if environment=='staging' else 'world.openfoodfacts.org'
    url=f'https://{host}/api/v3/product/{barcode}.json?fields={FIELDS}'
    saved=cache/(hashlib.sha256(url.encode()).hexdigest()+'.json')
    with (cache/'.off-lookup.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        now=clock()
        if saved.exists():
            value=json.loads(saved.read_text());age=now-value['fetchedAtEpoch']
            if 0<=age<=max_age_hours*3600:return {'status':'cache_hit','record':value}
        ledger=cache/'off-product-requests.json';times=json.loads(ledger.read_text()) if ledger.exists() else []
        recent=[t for t in times if now-60<t<=now]
        # 12/minute, spaced >=5s: below the publisher's 15 product reads/minute limit.
        if recent and (now-recent[-1]<5 or len(recent)>=12):raise ValueError('OFF lookup rate limit; try again later')
        ledger.write_text(json.dumps(recent+[now]))
        headers={'User-Agent':'AgoraFoodPlanner/0.1 (https://github.com/josephm1/Agora)','Accept':'application/json'}
        if environment=='staging':headers['Authorization']='Basic '+base64.b64encode(b'off:off').decode()
        with opener(Request(url,headers=headers),timeout=30) as response:
            if response.geturl().split('/')[2]!=host:raise ValueError('Unexpected OFF redirect')
            raw=response.read(1000001)
        if len(raw)>1000000:raise ValueError('OFF response exceeds limit')
        result=json.loads(raw);product=result.get('product')
        if product is not None and (not isinstance(product,dict) or str(product.get('code'))!=barcode):raise ValueError('OFF barcode mismatch')
        value={'provider':'Open Food Facts','attribution':'Open Food Facts contributors, ODbL','environment':environment,'sourceUrl':url.split('?')[0],'fetchedAt':datetime.fromtimestamp(now,timezone.utc).isoformat(),'fetchedAtEpoch':now,'barcode':barcode,'found':isinstance(product,dict),'data':product or {}}
        temporary=saved.with_suffix('.tmp');temporary.write_text(json.dumps(value,ensure_ascii=False));temporary.replace(saved)
        return {'status':'refreshed','record':value}

def refresh(snapshot,pilot,destination,barcode,product_id,cache,environment='staging',category_map=None,**kwargs):
    from prepare_catalogue import prepare
    rows=json.loads(Path(snapshot).read_text()).get('products',[])
    if not any(str(row.get('id'))==product_id for row in rows):raise ValueError('Choose an existing Lidl product')
    receipt=lookup(barcode,cache,environment,**kwargs);record=receipt['record']
    pilot=Path(pilot);value=json.loads(pilot.read_text()) if pilot.exists() else {'products':[]}
    if value.get('environment') not in (None,environment):raise ValueError('Keep staging and production enrichment in separate files')
    value['environment']=environment;value['products']=[r for r in value.get('products',[]) if str((r.get('lidl') or {}).get('id'))!=product_id]
    value['products'].append({'lidl':{'id':product_id},'candidates':[{'data':record['data'],'source_url':record['sourceUrl'],'fetched_at_utc':record['fetchedAt'],'conflicts':[],'lookupMethod':'user_supplied_barcode'}] if record['found'] else []})
    pilot.parent.mkdir(parents=True,exist_ok=True)
    with pilot.with_suffix('.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        # Re-read while holding the write fence to preserve unrelated concurrent enrichments.
        current=json.loads(pilot.read_text()) if pilot.exists() else {'products':[]}
        if current.get('environment') not in (None,environment):raise ValueError('Environment changed')
        value['products']=[r for r in current.get('products',[]) if str((r.get('lidl') or {}).get('id'))!=product_id]+[value['products'][-1]]
        temporary=pilot.with_suffix('.tmp');temporary.write_text(json.dumps(value,ensure_ascii=False));temporary.replace(pilot)
        prepared=prepare(snapshot,destination,category_map,pilot)
    return {'status':receipt['status'],'found':record['found'],'catalogue':prepared,'message':'OFF lookup saved locally. Reload the catalogue. Barcode-to-Lidl identity still requires review.'}
def refresh_name(snapshot,pilot,destination,product_name,cache,environment='staging',category_map=None):
    rows=json.loads(Path(snapshot).read_text()).get('products',[])
    matches=[row for row in rows if row.get('title')==product_name]
    if len(matches)!=1:raise ValueError('Choose an exact unique local product name')
    identity=str(matches[0]['id'])
    existing=json.loads(Path(pilot).read_text())
    if existing.get('environment')!=environment:raise ValueError('Choose the matching OFF environment')
    item=next((r for r in existing.get('products',[]) if str((r.get('lidl') or {}).get('id'))==identity),None)
    codes={str((c.get('data') or {}).get('code')) for c in (item or {}).get('candidates',[]) if (c.get('data') or {}).get('code')}
    if len(codes)!=1:raise ValueError('No unique cached barcode candidate. Supply a barcode locally first.')
    return refresh(snapshot,pilot,destination,codes.pop(),identity,cache,environment,category_map)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('snapshot',type=Path);parser.add_argument('pilot',type=Path);parser.add_argument('destination',type=Path);parser.add_argument('--barcode');parser.add_argument('--product-id');parser.add_argument('--product-name');parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--environment',choices=['staging','production'],default='staging');parser.add_argument('--category-map',type=Path);args=vars(parser.parse_args())
    if args.get('product_name'):
        if args.get('barcode') or args.get('product_id'):parser.error('Use product-name or barcode/product-id')
        args.pop('barcode');args.pop('product_id');receipt=refresh_name(**args)
    else:
        args.pop('product_name')
        if not args.get('barcode') or not args.get('product_id'):parser.error('Provide product-name or both barcode and product-id')
        receipt=refresh(**args)
    print(json.dumps(receipt))
