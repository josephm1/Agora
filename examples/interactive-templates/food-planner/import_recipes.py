#!/usr/bin/env python3
"""Import source Recipe JSON-LD from saved HTML; no AI or automatic network calls."""
import argparse
from datetime import datetime,timezone
from fractions import Fraction
from html import unescape
from html.parser import HTMLParser
import hashlib,json,re
import fcntl
from pathlib import Path
from urllib.parse import urlparse
from recipe_metadata import metadata,metric
from recipe_index import read_entries,write_entries

class Scripts(HTMLParser):
    def __init__(self):super().__init__();self.collect=False;self.text='';self.blocks=[]
    def handle_starttag(self,tag,attrs):
        if tag=='script' and dict(attrs).get('type','').lower()=='application/ld+json':self.collect=True;self.text=''
    def handle_data(self,text):
        if self.collect:self.text+=text
    def handle_endtag(self,tag):
        if tag=='script' and self.collect:self.blocks.append(self.text);self.collect=False

def clean(value):return unescape(re.sub('<[^>]*>','',str(value))).strip()
def recipes(value):
    if isinstance(value,list):
        for item in value:yield from recipes(item)
    elif isinstance(value,dict):
        kinds=value.get('@type',[]);kinds=[kinds] if isinstance(kinds,str) else kinds
        if 'Recipe' in kinds:yield value
        else:
            for key in ('@graph','mainEntity','itemListElement','item'):
                if key in value:yield from recipes(value[key])
def steps(value):
    if isinstance(value,str):return [clean(value)] if clean(value) else []
    if isinstance(value,list):return [step for item in value for step in steps(item)]
    if isinstance(value,dict):return steps(value.get('itemListElement')) if 'itemListElement' in value else steps(value.get('text',''))
    return []
def ingredient(raw):
    original=clean(metric(raw));text=original.replace('½',' 1/2').replace('¼',' 1/4').replace('¾',' 3/4')
    text=re.sub(r'\s+',' ',text).strip();match=re.match(r'^(\d+(?:\s+\d+/\d+|/\d+|[.]\d+)?)\s*(.+)$',text)
    result={'name':re.sub(r'^[\d\s./–-]+','',text).strip() or original,'quantity':None,'unit':'unspecified','original':original,'optional':bool(re.search(r'\boptional\b',original,re.I))}
    if not match:return result
    number,rest=match.groups()
    try:quantity=float(sum(Fraction(part) for part in number.split()))
    except (ValueError,ZeroDivisionError):return result
    if not 0<quantity<=1000000:return result
    aliases={'g':'g','gram':'g','grams':'g','kg':'kg','kilogram':'kg','kilograms':'kg','ml':'ml','millilitres':'ml','l':'l','litre':'l','litres':'l','tsp':'tsp','teaspoon':'tsp','teaspoons':'tsp','tbsp':'tbsp','tablespoon':'tbsp','tablespoons':'tbsp','cup':'cup','cups':'cup'}
    first,*tail=rest.split(' ',1)
    if first.lower().rstrip('.') in aliases:
        result.update(quantity=quantity,unit=aliases[first.lower().rstrip('.')],name=tail[0].strip() if tail else original)
    elif re.match(r'^[a-zA-Z]',rest) and not re.match(r'^(?:oz|ounce|ounces|lb|lbs|pounds|cl|pinch|dash|handful)\b',rest,re.I):
        result.update(quantity=quantity,unit='piece',name=rest)
    return result

def import_recipe(html_path,url,destination,recipe_index=0):
    parsed=urlparse(url)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password:raise ValueError('Provide the actual HTTPS source URL')
    raw=Path(html_path).read_bytes()
    if len(raw)>2000000:raise ValueError('Source HTML exceeds 2 MB')
    parser=Scripts();parser.feed(raw.decode('utf-8',errors='strict'));found=[]
    for block in parser.blocks:
        try:found.extend(recipes(json.loads(block)))
        except (ValueError,TypeError):continue
    if not 0<=recipe_index<len(found):raise ValueError('No supported Recipe JSON-LD at that index')
    original=found[recipe_index];ingredients=original.get('recipeIngredient');instructions=steps(original.get('recipeInstructions'))
    if not isinstance(ingredients,list) or not ingredients or not instructions:raise ValueError('Source recipe needs ingredients and instructions')
    meta=metadata(original,raw.decode('utf-8'),url);yield_text=meta['yield']
    match=re.search(r'\d+',str(yield_text or ''))
    if not match:raise ValueError('Source serving count is unknown; do not invent one')
    servings=int(match.group())
    if not 1<=servings<=24:raise ValueError('Unsupported source serving count')
    digest=hashlib.sha256((url+json.dumps(original,sort_keys=True)).encode()).hexdigest()[:20];identity='source-'+digest
    tags=meta['equipmentTags']
    value={**{k:v for k,v in meta.items() if k!='yield'},'id':identity,'title':clean(original.get('name','')),'servings':servings,
           'ingredients':[ingredient(item) for item in ingredients],'steps':instructions,
           'source':{'kind':'imported','url':url,'retrievedAt':datetime.now(timezone.utc).isoformat(),'contentSha256':hashlib.sha256(raw).hexdigest()},
           'sourceIngredients':[clean(metric(item)) for item in ingredients], 'sourceAuthor':original.get('author'), 'sourceNutrition':original.get('nutrition'),'sourceDiet':original.get('suitableForDiet'),
           'sourceYield':original.get('recipeYield') or meta['yield'],'sourceCategory':original.get('recipeCategory'),'sourceCuisine':original.get('recipeCuisine')}
    if not value['title'] or len(json.dumps(value).encode())>32000:raise ValueError('Recipe is empty or too large')
    destination=Path(destination);folder=destination/'recipes';folder.mkdir(parents=True,exist_ok=True);path=folder/(identity+'.json')
    with (destination/'.recipes-index.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        if not path.exists():
            temporary_recipe=path.with_suffix('.json.tmp');temporary_recipe.write_text(json.dumps(value,ensure_ascii=False));temporary_recipe.replace(path)
        entries=[entry for entry in read_entries(destination) if entry.get('sourceUrl')!=url]+[{'id':identity,'title':value['title'],'sourceUrl':url,'path':'recipes/'+path.name,'category':value['sourceCategory'],'equipmentTags':tags,'dietTags':meta['dietTags'],'caloriesPerServing':meta['caloriesPerServing'],'calorieBasis':meta['calorieBasis']}]
        write_entries(destination,entries)
    return {'status':'imported','recipeId':identity,'path':'recipes/'+path.name}
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('html',type=Path);parser.add_argument('destination',type=Path);parser.add_argument('--url',required=True);parser.add_argument('--recipe-index',type=int,default=0)
    args=parser.parse_args();print(json.dumps(import_recipe(args.html,args.url,args.destination,args.recipe_index)))
