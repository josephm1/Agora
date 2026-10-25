#!/usr/bin/env python3
"""Prepare dated, category-scoped saved Lidl snapshots. No HTTP or AI calls.

Run your existing Lidl fetcher first. This adapter never replaces it. An optional
category map assigns exact product IDs when the snapshot has no category labels.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import tempfile
import unicodedata

SCHEMA = 'agora.food-catalogue/v1'

def slug(value):
    text = unicodedata.normalize('NFKD', str(value)).encode('ascii', 'ignore').decode().lower()
    return re.sub(r'[^a-z0-9]+', '-', text).strip('-')[:48] or 'uncategorised'

def pack(annotations):
    for annotation in reversed(annotations or []):
        m=re.fullmatch(r'\s*(?:(\d+)\s*[x×]\s*)?(\d+(?:[.,]\d+)?)\s*(kg|g|ml|cl|l)\s*', str(annotation).lower())
        if m:
            count,amount,unit=m.groups();q=int(count or 1)*float(amount.replace(',','.'))
            if unit=='cl':q*=10;unit='ml'
            if math.isfinite(q) and 0<q<=1000000:return {'quantity':q,'unit':unit}
    return None

def normalize(product, off=None):
    price=product.get('price') or {}; amount=price.get('price')
    if isinstance(amount,bool) or not isinstance(amount,(int,float)) or not math.isfinite(amount) or amount<0:amount=None
    currency='EUR' if price.get('symbol')=='€' else None
    return {'id':str(product['id']), 'title':str(product['title'])[:160], 'brand':str(product.get('brand') or '')[:100],
            'price':amount, 'currency':currency, 'pack':pack(price.get('annotations')),
            'nutritionMatch':'not_verified', 'openFoodFacts':off}

def hierarchy(product,mapping):
    selected=mapping.get(str(product['id']))
    if selected is not None:
        if not isinstance(selected,list) or len(selected)!=2 or not all(isinstance(s,str) and s.strip() for s in selected):
            raise ValueError('Category mapping must be product ID -> [category, subcategory]')
        return tuple(selected)
    # Only use explicit label fields, never an LLM guess or the product's name.
    category=product.get('categoryName') or product.get('category')
    subcategory=product.get('subcategoryName') or product.get('subcategory')
    if isinstance(category,str) and category.strip():
        return category,subcategory if isinstance(subcategory,str) and subcategory.strip() else 'General'
    return 'Uncategorised','Needs classification'

def encode(value):return json.dumps(value,ensure_ascii=False,separators=(',',':'),allow_nan=False).encode()

def prepare(snapshot_path, destination, mapping_path=None, off_pilot_path=None):
    raw=Path(snapshot_path).read_bytes(); source=json.loads(raw);mapping=json.loads(Path(mapping_path).read_text()) if mapping_path else {}
    if not isinstance(mapping,dict):raise ValueError('Category map must be an object')
    off_rows={};off_bytes=b''
    if off_pilot_path:
        off_bytes=Path(off_pilot_path).read_bytes();pilot=json.loads(off_bytes)
        for item in pilot.get('products',[]):
            product_id=str((item.get('lidl') or {}).get('id') or '')
            if not product_id:continue
            candidates=[]
            for candidate in (item.get('candidates') or [])[:3]:
                data=candidate.get('data') or {}
                fields={key:data.get(key) for key in ('code','product_name','quantity','ingredients_text','ingredients_text_en','allergens_tags','traces_tags','nutriments','nutriscore_grade','nova_group','additives_tags','data_quality_errors_tags')}
                candidates.append({'status':'unverified_candidate','sourceUrl':candidate.get('source_url'),'fetchedAt':candidate.get('fetched_at_utc'),'conflicts':candidate.get('conflicts') or [],'data':fields})
            off_rows[product_id]={'provider':'Open Food Facts','attribution':'Open Food Facts contributors, ODbL','environment':pilot.get('environment'),'matchStatus':'unverified_candidates' if candidates else 'no_candidate','candidates':candidates}

    products=source.get('products');fetched=source.get('fetchedAtUtc')
    if not isinstance(products,list) or not isinstance(fetched,str):raise ValueError('Snapshot needs products and fetchedAtUtc')
    timestamp=datetime.fromisoformat(fetched.replace('Z','+00:00'))
    if timestamp.tzinfo is None:raise ValueError('Snapshot timestamp needs a timezone')
    stamp=timestamp.astimezone(timezone.utc).strftime('%Y-%m-%dT%H%M%SZ')
    store=str(source.get('storeId') or 'unknown');digest=hashlib.sha256(raw+encode(mapping)+off_bytes).hexdigest()[:12]
    identity=f'{stamp}-{slug(store)}-{digest}'
    destination=Path(destination);destination.mkdir(parents=True,exist_ok=True)
    relative=f'catalogues/{identity}';target=destination/relative
    categories={};seen=set()
    for product in products:
        if not isinstance(product,dict) or not product.get('id') or not product.get('title'):raise ValueError('Product missing ID/title')
        row=normalize(product, off_rows.get(str(product['id'])))
        if row['id'] in seen:raise ValueError('Duplicate product ID')
        seen.add(row['id']);category,subcategory=hierarchy(product,mapping)
        categories.setdefault((category,subcategory),[]).append(row)
    metadata={'schema':SCHEMA,'storeId':store,'fetchedAt':fetched,'snapshotId':identity,
              'sourceSha256':hashlib.sha256(raw).hexdigest(),'totalProducts':len(products),'complete':isinstance(source.get('totalProducts'),int) and not isinstance(source.get('totalProducts'),bool) and source['totalProducts']==len(products),'categories':[]}
    staging=Path(tempfile.mkdtemp(prefix='.catalogue-',dir=destination))
    try:
        for number,((category,subcategory),rows) in enumerate(sorted(categories.items())):
            # Stable numeric prefix prevents collisions from normalized category labels.
            chunks=[];current=[]
            for row in rows:
                if current and (len(current)>=25 or len(encode({'products':current+[row]}))>80000):chunks.append(current);current=[]
                current.append(row)
            if current:chunks.append(current)
            paths=[]
            for page,chunk in enumerate(chunks):
                name=f'category-{number:03d}-{slug(category)}-{slug(subcategory)}-{page:03d}.json'
                body={'schema':'agora.food-products/v1','storeId':store,'fetchedAt':fetched,'category':category,'subcategory':subcategory,'products':chunk}
                if len(encode(body))>100000:raise ValueError('Category page too large')
                (staging/name).write_bytes(encode(body));paths.append(f'{relative}/{name}')
            metadata['categories'].append({'category':category,'subcategory':subcategory,'count':len(rows),'files':paths})
        if len(encode(metadata))>100000:raise ValueError('Catalogue index too large; reduce page metadata')
        (staging/'index.json').write_bytes(encode(metadata))
        target.parent.mkdir(exist_ok=True)
        if target.exists():
            if (target/'index.json').read_bytes()!=encode(metadata):raise ValueError('Existing dated snapshot has different classification; use a separate output folder')
            shutil.rmtree(staging)
        else:staging.rename(target)
        # Old dated snapshots remain. The top-level file is a small atomic latest pointer.
        pointer={'schema':SCHEMA,'storeId':store,'fetchedAt':fetched,'index':f'{relative}/index.json','snapshotId':identity,'complete':metadata['complete']}
        temporary=destination/'.catalogue-index.tmp';temporary.write_bytes(encode(pointer));temporary.replace(destination/'catalogue-index.json')
        return pointer
    finally:
        if staging.exists():shutil.rmtree(staging)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('snapshot',type=Path);parser.add_argument('destination',type=Path);parser.add_argument('--category-map',type=Path);parser.add_argument('--off-pilot',type=Path)
    args=parser.parse_args();print(json.dumps(prepare(args.snapshot,args.destination,args.category_map,args.off_pilot)))
