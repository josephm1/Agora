"""Small source summaries, sharded below the native readTextFile size limit."""
import hashlib,json
from pathlib import Path

def read_entries(destination):
    destination=Path(destination);path=destination/'recipes-index.json'
    if not path.exists():return []
    index=json.loads(path.read_text())
    if index.get('schema')=='agora.source-recipes/v1':return index['recipes']
    if index.get('schema')!='agora.source-recipes/v2':raise ValueError('Invalid recipe index')
    result=[]
    for relative in index['pages']:
        path=Path(relative)
        if path.is_absolute() or '..' in path.parts or path.parts[0]!='recipe-index':raise ValueError('Invalid index path')
        result.extend(json.loads((destination/path).read_text())['recipes'])
    return result

def write_entries(destination,entries):
    destination=Path(destination);pages=[];folder=destination/'recipe-index';folder.mkdir(exist_ok=True)
    entries=sorted(entries,key=lambda r:(r['title'].casefold(),r['id']))
    for start in range(0,len(entries),60):
        raw=json.dumps({'schema':'agora.recipe-page/v1','recipes':entries[start:start+60]},ensure_ascii=False).encode()
        if len(raw)>100000:raise ValueError('Source summary page exceeds limit')
        name='recipe-index/'+hashlib.sha256(raw).hexdigest()[:20]+'.json';path=destination/name
        if not path.exists():path.write_bytes(raw)
        pages.append(name)
    manifest={'schema':'agora.source-recipes/v2','count':len(entries),'pages':pages}
    temporary=destination/'.recipes-index.tmp';temporary.write_text(json.dumps(manifest));temporary.replace(destination/'recipes-index.json')
    return manifest
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('destination',type=Path);args=parser.parse_args();print(json.dumps(write_entries(args.destination,read_entries(args.destination))))
