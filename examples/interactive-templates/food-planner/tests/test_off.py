import io,json,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from refresh_off import lookup
class Response(io.BytesIO):
    def geturl(self):return 'https://world.openfoodfacts.net/api/v3/product/3017620422003.json'
class OffTest(unittest.TestCase):
    def test_fresh_barcode_cache_skips_http_and_other_barcodes_share_rate_fence(self):
        calls=[]
        def opener(request,**kwargs):
            calls.append(request);return Response(json.dumps({'product':{'code':'3017620422003','product_name':'Example'}}).encode())
        with tempfile.TemporaryDirectory() as d:
            first=lookup('3017620422003',d,opener=opener,clock=lambda:1000)
            self.assertEqual(first['status'],'refreshed');self.assertEqual(lookup('3017620422003',d,opener=opener,clock=lambda:1001)['status'],'cache_hit');self.assertEqual(len(calls),1)
            self.assertRaises(ValueError,lookup,'12345678',d,opener=opener,clock=lambda:1001)
    def test_bad_barcode_and_mismatched_response_do_not_replace_cache(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertRaises(ValueError,lookup,'../../other',d)
            def wrong(*args,**kwargs):return Response(b'{"product":{"code":"12345678"}}')
            self.assertRaises(ValueError,lookup,'3017620422003',d,opener=wrong,clock=lambda:1000)
            self.assertFalse(any(p.name.endswith('.json') and p.name!='off-product-requests.json' for p in Path(d).iterdir()))
if __name__=='__main__':unittest.main()

class OffIntegrationTest(unittest.TestCase):
    def test_explicit_refresh_rebuilds_local_catalogue_without_lidl_fetch_and_keeps_candidate_unverified(self):
        from refresh_off import refresh
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);snapshot=root/'snapshot.json';pilot=root/'off.json'
            snapshot.write_text(json.dumps({'storeId':'IE0206','totalProducts':1,'fetchedAtUtc':'2026-10-25T12:00:00Z','products':[{'id':'local-id','title':'Example food','category':'Pantry','subcategory':'Spread','price':{'price':2,'symbol':'€','annotations':['250g']}}]}))
            record={'found':True,'data':{'code':'3017620422003','product_name':'Example food'},'sourceUrl':'https://world.openfoodfacts.net/api/v3/product/3017620422003.json','fetchedAt':'2026-10-25T12:00:00Z'}
            with patch('refresh_off.lookup',return_value={'status':'cache_hit','record':record}):
                receipt=refresh(snapshot,pilot,root/'prepared','3017620422003','local-id',root/'cache')
            self.assertEqual(receipt['status'],'cache_hit')
            pointer=json.loads((root/'prepared/catalogue-index.json').read_text());index=json.loads((root/'prepared'/pointer['index']).read_text());page=json.loads((root/'prepared'/index['categories'][0]['files'][0]).read_text())
            off=page['products'][0]['openFoodFacts'];self.assertEqual(off['matchStatus'],'unverified_candidates');self.assertEqual(off['candidates'][0]['status'],'unverified_candidate')
            self.assertEqual(json.loads(snapshot.read_text())['products'][0]['id'],'local-id')
