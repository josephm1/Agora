import json,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from recipe_metadata import metadata
from recipe_index import read_entries,write_entries
from import_recipes import ingredient

class RecipeMetadataTest(unittest.TestCase):
    def test_recipe_diet_region_excludes_symbols_legend_and_zero_calories_are_unknown(self):
        html='<div class="special-diets"><img alt="Vegetarian"></div><div id="legend"><img alt="Vegan"></div>'
        value=metadata({'nutrition':{'calories':0}},html,'https://www.diabetes.org.uk/guide-to-diabetes/recipes/test')
        self.assertEqual(value['dietTags'],['vegetarian']);self.assertIsNone(value['caloriesPerServing'])
        html+='<div class="nutrition-tag calories"><dt>KCal</dt><dd class="nutrition-tag__content">313</dd></div>'
        value=metadata({},html,'https://www.diabetes.org.uk/living-with-diabetes/recipes/test')
        self.assertEqual(value['caloriesPerServing'],313);self.assertIn('per serving',value['calorieBasis'])
    def test_ninja_serving_table_metric_variant_and_footer_do_not_invent_model_compatibility(self):
        html='<div data-attr-icon="recipeServings"></div><div class="recipe-table-value">4</div><footer>Ninja Foodi 11-in-1</footer>'
        value=metadata({'name':'Mushroom risotto','recipeInstructions':['Use PRESSURE.']},html,'https://www.sharkninja.co.uk/mushroom-risotto/REC620EU.html')
        self.assertEqual(value['yield'],'4');self.assertIn('multi-cooker',value['equipmentTags']);self.assertNotIn('ninja-11-in-1',value['equipmentTags'])
        parsed=ingredient('<strong><span data-metric="400g" data-imperial="14oz">14oz</span></strong> Mixed mushrooms')
        self.assertEqual(parsed['quantity'],400);self.assertEqual(parsed['unit'],'g');self.assertEqual(parsed['name'],'Mixed mushrooms')
    def test_large_local_summary_index_is_paged_and_roundtrips_without_recipe_bodies(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);entries=[{'id':str(i),'title':'Recipe '+str(i),'sourceUrl':'https://source.test/'+str(i),'path':'recipes/'+str(i)+'.json','dietTags':[]} for i in range(650)]
            manifest=write_entries(root,entries);self.assertEqual(manifest['count'],650);self.assertEqual(len(manifest['pages']),11)
            result=read_entries(root);self.assertEqual({e['id'] for e in result},{e['id'] for e in entries})
            for file in manifest['pages']:
                self.assertLess((root/file).stat().st_size,100000);self.assertLessEqual(len(json.loads((root/file).read_text())['recipes']),60)
if __name__=='__main__':unittest.main()
