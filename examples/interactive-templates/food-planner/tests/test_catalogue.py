import importlib.util,json,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('prepare',ROOT/'prepare_catalogue.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class CatalogueTest(unittest.TestCase):
    def test_checked_in_template_matches_the_canonical_sources_and_fits_native_limits(self):
        spec=importlib.util.spec_from_file_location('pack',ROOT/'build_pack.py');pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)
        self.assertEqual(json.loads((ROOT/'food-planner.agora-template.json').read_text()),pack.template())
    def test_dated_categories_preserve_metadata_locally_without_duplicate_names_files(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);snapshot=root/'snapshot.json';mapping=root/'mapping.json';out=root/'prepared'
            rows=[{'id':'pasta','title':'Pasta','price':{'price':1.49,'symbol':'€','annotations':['500g']}},{'id':'fish','title':'Salmon','price':{'price':5,'symbol':'€','annotations':['2x125g']}}]
            snapshot.write_text(json.dumps({'storeId':'IE0206','fetchedAtUtc':'2026-10-25T12:00:00Z','products':rows}));mapping.write_text(json.dumps({'pasta':['Pantry','Pasta'],'fish':['Fresh','Fish']}))
            pointer=m.prepare(snapshot,out,mapping);index=json.loads((out/pointer['index']).read_text());self.assertEqual(len(index['categories']),2)
            pasta=next(c for c in index['categories'] if c['subcategory']=='Pasta');data=json.loads((out/pasta['files'][0]).read_text())
            self.assertEqual([p['title'] for p in data['products']],['Pasta']);self.assertEqual(data['products'][0]['price'],1.49);self.assertEqual(data['products'][0]['pack']['quantity'],500)
            self.assertEqual(list(out.rglob('*.txt')),[]);self.assertNotIn('aiFiles',pasta)
            old=pointer['index'];source=json.loads(snapshot.read_text());source['fetchedAtUtc']='2026-10-26T12:00:00Z';snapshot.write_text(json.dumps(source));new=m.prepare(snapshot,out,mapping)
            self.assertNotEqual(old,new['index']);self.assertTrue((out/old).exists());self.assertEqual(json.loads((out/'catalogue-index.json').read_text()),new)
    def test_open_food_facts_candidates_stay_local_and_unverified(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);source=root/'snapshot.json';off=root/'off.json'
            source.write_text(json.dumps({'storeId':'IE0206','fetchedAtUtc':'2026-10-25T12:00:00Z','products':[{'id':'pasta','title':'Pasta'}]}))
            off.write_text(json.dumps({'environment':'staging','products':[{'lidl':{'id':'pasta'},'candidates':[{'source_url':'https://world.openfoodfacts.net/product/123','fetched_at_utc':'2026-10-20T12:00:00Z','data':{'ingredients_text':'Candidate ingredients','nutriscore_grade':'a','nova_group':2,'nutriments':{'energy-kcal_100g':321}}}]}]}))
            pointer=m.prepare(source,root/'out',off_pilot_path=off);index=json.loads((root/'out'/pointer['index']).read_text());row=json.loads((root/'out'/index['categories'][0]['files'][0]).read_text())['products'][0]
            info=row['openFoodFacts'];self.assertEqual(info['provider'],'Open Food Facts');self.assertEqual(info['matchStatus'],'unverified_candidates');self.assertEqual(info['candidates'][0]['data']['nutriscore_grade'],'a');self.assertEqual(info['candidates'][0]['status'],'unverified_candidate')
    def test_no_category_is_invented_and_duplicate_ids_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);p=root/'snapshot.json';source={'products':[{'id':'one','title':'Pasta'}],'fetchedAtUtc':'2026-10-25T12:00:00Z'};p.write_text(json.dumps(source))
            pointer=m.prepare(p,root/'out');index=json.loads((root/'out'/pointer['index']).read_text());self.assertEqual(index['categories'][0]['category'],'Uncategorised')
            source['products']*=2;p.write_text(json.dumps(source));self.assertRaises(ValueError,m.prepare,p,root/'out')
if __name__=='__main__':unittest.main()
