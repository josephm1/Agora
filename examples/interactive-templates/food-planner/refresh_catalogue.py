#!/usr/bin/env python3
"""Reuse fresh complete snapshots; call the existing fetcher only when necessary.

No HTTP implementation is supplied here. The caller explicitly configures an argv
array for the existing fetch script, containing {output} for a temporary snapshot.
Never execute that command if the cached snapshot is fresh, complete and for the
requested store. Output is a small receipt, never product metadata.
"""
import argparse
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import subprocess
import tempfile
import math
from prepare_catalogue import prepare

# Cross-process refresh serialization on the Android/Linux sandbox and macOS.
import fcntl

def assess(path, store, max_age_hours, now=None):
    now=now or datetime.now(timezone.utc)
    try:
        data=json.loads(Path(path).read_text())
        if data.get('storeId')!=store:return False,'wrong_store'
        products=data.get('products');total=data.get('totalProducts')
        if isinstance(total,bool) or not isinstance(total,int) or total<=0 or not isinstance(products,list) or len(products)!=total:
            return False,'incomplete_count'
        ids=[]
        for product in products:
            if not isinstance(product,dict) or not isinstance(product.get('id'),str) or not product['id'].strip() or not isinstance(product.get('title'),str) or not product['title'].strip():
                return False,'invalid_product'
            ids.append(product['id'])
        if len(set(ids))!=total:return False,'duplicate_ids'
        fetched=datetime.fromisoformat(data['fetchedAtUtc'].replace('Z','+00:00'))
        if fetched.tzinfo is None:return False,'unknown_timezone'
        age=now-fetched
        if age < -timedelta(minutes=5):return False,'future_timestamp'
        if age>timedelta(hours=max_age_hours):return False,'stale'
        return True,'fresh_complete'
    except (OSError,ValueError,KeyError,TypeError,AttributeError):return False,'missing_or_invalid_snapshot'

def ensure(snapshot, destination, store, category_map=None, max_age_hours=72, command=None, runner=subprocess.run, now=None, off_pilot=None):
    if not isinstance(store,str) or not store.strip() or not math.isfinite(max_age_hours) or max_age_hours<=0:raise ValueError('Choose a store and positive cache age')
    snapshot=Path(snapshot);snapshot.parent.mkdir(parents=True,exist_ok=True)
    with snapshot.with_suffix(snapshot.suffix+'.refresh.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        # Recheck after locking: a simultaneous request may already have refreshed it.
        usable,reason=assess(snapshot,store,max_age_hours,now)
        if usable:
            pointer=prepare(snapshot,destination,category_map,off_pilot)
            return {'status':'cache_hit','fetchInvoked':False,'snapshotId':pointer['snapshotId']}
        if not isinstance(command,list) or not command or not all(isinstance(arg,str) and arg for arg in command) or not any('{output}' in arg for arg in command):
            raise ValueError('Cache is '+reason+'; configure the existing fetch argv with an {output} placeholder')
        # The fetcher writes a candidate. Never truncate the prior usable snapshot.
        with tempfile.TemporaryDirectory(prefix='.lidl-refresh-',dir=snapshot.parent) as work:
            candidate=Path(work)/'snapshot.json'
            argv=[arg.replace('{output}',str(candidate)).replace('{store}',store) for arg in command]
            result=runner(argv,check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=180)
            if result.returncode!=0:raise RuntimeError('Lidl fetch failed; previous cached data was preserved')
            valid,failure=assess(candidate,store,max_age_hours,now)
            if not valid:raise ValueError('Fetched snapshot is '+failure+'; previous cached data was preserved')
            # Preparation must succeed before replacing the current raw cache.
            pointer=prepare(candidate,destination,category_map,off_pilot)
            candidate.replace(snapshot)
        return {'status':'refreshed','fetchInvoked':True,'previousCacheReason':reason,'snapshotId':pointer['snapshotId']}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('snapshot',type=Path);parser.add_argument('destination',type=Path)
    parser.add_argument('--store',required=True);parser.add_argument('--category-map',type=Path);parser.add_argument('--max-age-hours',type=float,default=72)
    parser.add_argument('--off-pilot',type=Path)
    parser.add_argument('--fetch-command-json',help='Explicit existing fetch command as a JSON argv array; no shell evaluation')
    args=parser.parse_args()
    command=json.loads(args.fetch_command_json) if args.fetch_command_json else None
    print(json.dumps(ensure(args.snapshot,args.destination,args.store,args.category_map,args.max_age_hours,command,off_pilot=args.off_pilot)))
