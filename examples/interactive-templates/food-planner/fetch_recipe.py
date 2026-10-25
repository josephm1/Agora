#!/usr/bin/env python3
"""Explicit source import through the existing script runner, never an AI recipe generator."""
import argparse,json,tempfile
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request,urlopen
from import_recipes import import_recipe
from recipe_index import read_entries

def fetch(url,destination,opener=urlopen,refresh=False):
    parsed=urlparse(url)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password:raise ValueError('Use an explicit HTTPS recipe source URL')
    destination=Path(destination);index=destination/'recipes-index.json'
    if index.exists() and not refresh:
        entries=read_entries(destination)
        for entry in entries:
            if entry.get('sourceUrl')!=url:continue
            relative=Path(entry.get('path',''))
            if relative.is_absolute() or '..' in relative.parts or not relative.parts or relative.parts[0]!='recipes':raise ValueError('Invalid cached recipe path')
            cached=json.loads((destination/relative).read_text())
            if cached.get('id')!=entry.get('id') or cached.get('source',{}).get('url')!=url:raise ValueError('Cached recipe identity is invalid')
            return {'status':'cache_hit','recipeId':cached['id'],'path':str(relative)}
    with opener(Request(url,headers={'User-Agent':'AgoraRecipeImporter/0.1','Accept':'text/html'}),timeout=30) as response:
        final=response.geturl()
        if urlparse(final).scheme!='https':raise ValueError('Source redirected away from HTTPS')
        body=response.read(2000001)
    if len(body)>2000000:raise ValueError('Source exceeds 2 MB')
    with tempfile.TemporaryDirectory() as d:
        html=Path(d)/'recipe.html';html.write_bytes(body)
        return import_recipe(html,final,destination)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('url');parser.add_argument('destination',type=Path);parser.add_argument('--refresh',action='store_true');args=parser.parse_args();print(json.dumps(fetch(args.url,args.destination,refresh=args.refresh)))
