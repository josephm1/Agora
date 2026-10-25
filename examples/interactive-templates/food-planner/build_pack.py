#!/usr/bin/env python3
"""Build a self-contained Skill/template import. No provider or network access."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
ROOT=Path(__file__).resolve().parent
TEMPLATE_ID='c8e6489c-c780-4aeb-b449-a8bd2bcb19d1'

def template():
    code=(ROOT/'engine.js').read_text()+'\nvar FOOD_EXAMPLE='+json.dumps(json.loads((ROOT/'example.json').read_text()),separators=(',',':'))+';\n'+(ROOT/'planner.js').read_text()
    value={'kind':'agora.interactive-template','version':1,'id':TEMPLATE_ID,'name':'Food planner',
           'description':'Plan meals and manage a shopping list','sourceFilesAuto':False,'resourceFiles':[],
           'answer':{'title':'Food planner','description':'Menus recipes and an editable shopping list',
                     'fallback':'Open the food planner to browse meals, inspect recipes, edit servings and manage a saved shopping list. AI suggestions require an explicit button and review before Apply.',
                     'html':(ROOT/'planner.html').read_text(),'css':(ROOT/'planner.css').read_text(),
                     'jsFunctions':code,'jsExpressions':['initFoodPlanner()'],'eInkAware':True}}
    assert len(json.dumps(value['answer'],ensure_ascii=False).encode())<80000
    assert len(json.dumps(value,ensure_ascii=False).encode())<100000
    return value

def build(output):
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    value=template()
    with ZipFile(output,'w',ZIP_DEFLATED) as archive:
        archive.writestr('manifest.json',json.dumps({'agora_export_version':6,'app_version':'2.1.0','exported_at':datetime.now(timezone.utc).isoformat(),'categories':['memories','templates'],'has_api_keys':False}))
        archive.writestr('templates/catalog.json',json.dumps([value]))
        archive.writestr('memories/skill_db/food-planner.md',(ROOT/'food-planner.md').read_text())
        archive.writestr('memories/skill_db/skill_meta.json',json.dumps({'food-planner.md':'Plan meals using selected product names','@shortcut:food-planner.md':'food'}))
    output.with_suffix('.agora-template.json').write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    return output
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('output',type=Path);parser.add_argument('--update-template',action='store_true');args=parser.parse_args()
    if args.update_template:(ROOT/'food-planner.agora-template.json').write_text(json.dumps(template(),ensure_ascii=False,indent=2)+'\n')
    print(build(args.output))
