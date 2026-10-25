from datetime import datetime,timezone
import importlib.util,json,sys,tempfile,unittest
from pathlib import Path
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
import refresh_catalogue as refresh
NOW=datetime(2026,10,25,12,tzinfo=timezone.utc)
def snapshot():return {'storeId':'IE0206','totalProducts':2,'fetchedAtUtc':NOW.isoformat(),'products':[{'id':'pasta','title':'Pasta'},{'id':'eggs','title':'Eggs'}]}
class RefreshTest(unittest.TestCase):
    def test_fresh_complete_data_never_invokes_the_fetcher(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);path=root/'snapshot.json';path.write_text(json.dumps(snapshot()))
            def forbidden(*args,**kwargs):raise AssertionError('A fresh complete cache must never call Lidl')
            result=refresh.ensure(path,root/'prepared','IE0206',command=['fetch','{output}'],runner=forbidden,now=NOW)
            self.assertFalse(result['fetchInvoked']);self.assertEqual(result['status'],'cache_hit')
    def test_stale_incomplete_and_wrong_store_data_refresh_once(self):
        for field,value in [('fetchedAtUtc','2026-10-20T12:00:00Z'),('totalProducts',3),('storeId','OTHER')]:
            with self.subTest(field=field),tempfile.TemporaryDirectory() as d:
                root=Path(d);path=root/'snapshot.json';data=snapshot();data[field]=value;path.write_text(json.dumps(data));calls=[]
                def fetch(argv,**kwargs):calls.append(argv);Path(argv[1]).write_text(json.dumps(snapshot()));return SimpleNamespace(returncode=0)
                result=refresh.ensure(path,root/'prepared','IE0206',command=['fetch','{output}'],runner=fetch,now=NOW)
                self.assertTrue(result['fetchInvoked']);self.assertEqual(len(calls),1);self.assertTrue(refresh.assess(path,'IE0206',72,NOW)[0])
                refresh.ensure(path,root/'prepared','IE0206',command=['fetch','{output}'],runner=fetch,now=NOW);self.assertEqual(len(calls),1)
    def test_bad_refresh_preserves_the_old_snapshot(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);path=root/'snapshot.json';old=snapshot();old['totalProducts']=3;raw=json.dumps(old);path.write_text(raw)
            def incomplete(argv,**kwargs):Path(argv[1]).write_text(raw);return SimpleNamespace(returncode=0)
            with self.assertRaisesRegex(ValueError,'incomplete_count'):refresh.ensure(path,root/'prepared','IE0206',command=['fetch','{output}'],runner=incomplete,now=NOW)
            self.assertEqual(path.read_text(),raw);self.assertFalse((root/'prepared/catalogue-index.json').exists())
    def test_concurrent_requests_share_one_refresh_and_recheck_after_locking(self):
        from concurrent.futures import ThreadPoolExecutor
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);path=root/'snapshot.json';calls=[]
            def fetch(argv,**kwargs):
                calls.append(argv);Path(argv[1]).write_text(json.dumps(snapshot()));return SimpleNamespace(returncode=0)
            def run(_):return refresh.ensure(path,root/'prepared','IE0206',command=['fetch','{output}'],runner=fetch,now=NOW)
            with ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(run,range(4)))
            self.assertEqual(len(calls),1);self.assertEqual(sum(result['fetchInvoked'] for result in results),1)
    def test_unknown_completeness_duplicate_ids_and_future_dates_are_not_silently_accepted(self):
        for change in [{'totalProducts':None},{'products':[{'id':'pasta','title':'One'},{'id':'pasta','title':'Two'}]},{'fetchedAtUtc':'2026-10-27T12:00:00Z'}]:
            with tempfile.TemporaryDirectory() as d:
                path=Path(d)/'snapshot.json';data=snapshot();data.update(change);path.write_text(json.dumps(data));self.assertFalse(refresh.assess(path,'IE0206',72,NOW)[0])
if __name__=='__main__':unittest.main()
