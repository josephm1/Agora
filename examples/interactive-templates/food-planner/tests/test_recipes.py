import json,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from import_recipes import import_recipe,ingredient
class RecipesTest(unittest.TestCase):
    def test_source_recipe_is_imported_with_original_content_and_ninja_focus(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);html=root/'recipe.html';recipe={'@type':'Recipe','name':'Ninja 11-in-1 pasta','recipeYield':'2 servings','recipeIngredient':['200g pasta','1 tbsp oil','Salt, to taste'],'recipeInstructions':[{'@type':'HowToStep','text':'Follow the source cooking instruction.'}],'nutrition':{'calories':'600 calories'}}
            html.write_text('<script type="application/ld+json">'+json.dumps({'@graph':[recipe]})+'</script>')
            receipt=import_recipe(html,'https://recipes.example.test/ninja',root/'prepared');saved=json.loads((root/'prepared'/receipt['path']).read_text())
            self.assertEqual(saved['steps'],['Follow the source cooking instruction.']);self.assertEqual(saved['sourceIngredients'],recipe['recipeIngredient']);self.assertEqual(saved['ingredients'][0]['quantity'],200);self.assertEqual(saved['ingredients'][0]['unit'],'g');self.assertEqual(saved['ingredients'][2]['quantity'],None)
            self.assertIn('ninja-11-in-1',saved['equipmentTags']);self.assertEqual(saved['source']['kind'],'imported');self.assertEqual(saved['source']['url'],'https://recipes.example.test/ninja')
            from fetch_recipe import fetch
            def forbidden(*args,**kwargs):raise AssertionError('An imported source should not be fetched again')
            self.assertEqual(fetch('https://recipes.example.test/ninja',root/'prepared',opener=forbidden)['status'],'cache_hit')
    def test_missing_structured_recipe_or_servings_is_not_filled_by_guessing(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);html=root/'plain.html';html.write_text('<p>No structured recipe here</p>');self.assertRaises(ValueError,import_recipe,html,'https://recipes.example.test/plain',root/'out')
            self.assertEqual(ingredient('Salt to taste')['quantity'],None)
if __name__=='__main__':unittest.main()
