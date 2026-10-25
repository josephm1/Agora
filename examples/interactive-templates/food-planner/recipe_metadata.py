"""Conservative publisher metadata: never infer diet from a title or site-wide legend."""
import re
from html import unescape
from html.parser import HTMLParser
from urllib.parse import urlparse

class Node(HTMLParser):
    def __init__(self):
        super().__init__();self.root={'tag':'root','attrs':{},'children':[]};self.stack=[self.root]
    def handle_starttag(self,tag,attrs):
        node={'tag':tag,'attrs':dict(attrs),'children':[]};self.stack[-1]['children'].append(node)
        if tag not in {'img','input','meta','link','br','hr','source','area','embed','wbr'}:self.stack.append(node)
    def handle_endtag(self,tag):
        for n in range(len(self.stack)-1,0,-1):
            if self.stack[n]['tag']==tag:self.stack=self.stack[:n];break
    def handle_data(self,data):self.stack[-1]['children'].append(data)
def nodes(node):
    if isinstance(node,dict):
        yield node
        for child in node['children']:yield from nodes(child)
def text(node):return node if isinstance(node,str) else ' '.join(text(c) for c in node['children'])
def classes(node):return node['attrs'].get('class','').split()
def metric(raw):
    return re.sub(r'<span\b[^>]*\bdata-metric=["\']([^"\']*)["\'][^>]*>.*?</span>',lambda m:unescape(m[1]),raw,flags=re.S|re.I)
def metadata(original,html,url):
    host=urlparse(url).hostname;tree=Node();tree.feed(html);allnodes=list(nodes(tree.root))
    yield_text=original.get('recipeYield');yield_text=yield_text[0] if isinstance(yield_text,list) and yield_text else yield_text
    diets=[];source_diet=str(original.get('suitableForDiet','')).lower()
    if 'vegandiet' in source_diet:diets=['vegan','vegetarian']
    elif 'vegetariandiet' in source_diet:diets=['vegetarian']
    calories=None;basis=None
    # Schema Recipe nutrition describes one serving; empty/zero placeholder values remain unknown.
    nutrition=original.get('nutrition') or {};raw_cal=nutrition.get('calories','') if isinstance(nutrition,dict) else ''
    number=re.search(r'\d+(?:\.\d+)?',str(raw_cal))
    if number and 0<float(number[0])<=6000:calories=float(number[0]);basis='source Recipe nutrition per serving'
    if host=='www.diabetes.org.uk':
        region=next((n for n in allnodes if 'special-diets' in classes(n)),None)
        labels=[n['attrs'].get('alt','').lower() for n in nodes(region) if n['tag']=='img'] if region else []
        if 'vegan' in labels:diets=['vegan','vegetarian']
        elif 'vegetarian' in labels:diets=['vegetarian']
        region=next((n for n in allnodes if 'nutrition-tag' in classes(n) and 'calories' in classes(n)),None)
        value=next((text(n).strip() for n in nodes(region) if n['tag']=='dd'),None) if region else None
        if value and re.fullmatch(r'\d+(?:\.\d+)?',value) and 0<float(value)<=6000:
            calories=float(value);basis='publisher nutrition panel per serving (may include serving suggestion)'
    if host=='www.sharkninja.co.uk' and not yield_text:
        match=re.search(r'data-attr-icon="recipeServings"[^>]*>.*?</div>\s*<div class="recipe-table-value">\s*(\d+)\s*</div>',html,re.S)
        if match:yield_text=match[1]
    declared=' '.join([str(original.get('name','')),str(original.get('description','')),str(original.get('keywords','')),str(original.get('recipeInstructions',''))]).lower()
    tags=['ninja'] if 'ninja' in declared or host in {'www.sharkninja.co.uk','ninja-recipes.uk'} else []
    if 'ninja' in tags and re.search(r'11[ -]?(?:in|en)[ -]?1',declared):tags.append('ninja-11-in-1')
    if 'ninja' in tags and re.search(r'pressure|sear/saut|slow cook|multi.cooker|smartlid',declared):tags.append('multi-cooker')
    return {'yield':yield_text,'dietTags':diets,'caloriesPerServing':calories,'calorieBasis':basis,'equipmentTags':tags}
