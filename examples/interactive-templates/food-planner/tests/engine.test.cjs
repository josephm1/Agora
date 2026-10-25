const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const engine=require('../engine.js'),root=path.join(__dirname,'..');
const sample=()=>JSON.parse(fs.readFileSync(path.join(root,'example.json'),'utf8'));
function sourced(){const p=sample();p.recipes[0].source={kind:'imported',url:'https://recipes.example.test/pasta'};p.recipes[0].ingredients[1].optional=true;p.recipes.push({...engine.clone(p.recipes[0]),id:'source-other',title:'Another sourced meal'});p.recipeSelection=['source-other'];p.products.push({...p.products[0],id:'alternate',title:'Alternate pasta',price:3});return p;}
const proposal=extra=>({schema:'agora.food-proposal/v1',requestId:'new',baseRevision:1,slotId:'monday-dinner',...extra});
test('servings, units and pack prices are deterministic and incompatible units stay separate',()=>{
 const p=sample();p.menu[0].servings=4;p.extras.push({name:'Pasta',quantity:.1,unit:'kg',productId:'example-pasta-pack'},{name:'Tomatoes',quantity:100,unit:'ml'});
 const rows=engine.shopping(p),pasta=rows.find(r=>r.name==='Pasta');assert.equal(pasta.quantity,500);assert.equal(pasta.packs,1);assert.equal(pasta.cost,2);assert.equal(rows.filter(r=>r.name==='Tomatoes').length,2);
});
test('quantity changes do not retain a purchased marker for a different quantity',()=>{const p=sample(),row=engine.shopping(p)[0];p.checked[row.key]=true;assert.equal(engine.shopping(p)[0].checked,true);p.menu[0].servings=4;assert.equal(engine.shopping(p)[0].checked,false);});
test('unknown source quantities stay unknown instead of becoming invented shopping amounts',()=>{const p=sample();p.recipes[0].ingredients[0].quantity=null;const row=engine.shopping(p).find(r=>r.name==='Pasta');assert.equal(row.quantity,null);assert.equal(row.cost,null);});
test('AI recipe bodies are rejected; replacement selects an existing source recipe and checks revision',()=>{
 const p=sourced();assert.throws(()=>engine.proposal(p,proposal({action:'replace',recipe:{title:'Invented'}}),1),/AI-written/);
 const next=engine.proposal(p,proposal({action:'replace',sourceRecipeId:'source-other'}),1);assert.equal(next.menu[0].recipeId,'source-other');assert.equal(p.menu[0].recipeId,'example-pasta');
 assert.throws(()=>engine.proposal(p,proposal({action:'replace',sourceRecipeId:'source-other'}),2),/stale/);
 assert.throws(()=>engine.proposal(p,proposal({action:'replace',sourceRecipeId:'nonexistent'}),1),/source/);
});
test('product swaps preserve source quantities and steps and recalculate costs locally',()=>{
 const p=sourced(),before=p.recipes[0];const next=engine.proposal(p,proposal({action:'replace_product',ingredientName:'Pasta',productName:'Alternate pasta'}),1);
 const recipe=next.recipes.find(r=>r.id===next.menu[0].recipeId);assert.deepEqual(recipe.steps,before.steps);assert.equal(recipe.ingredients[0].quantity,before.ingredients[0].quantity);assert.equal(engine.shopping(next).find(r=>r.name==='Pasta').cost,3);
});
test('only source-labelled optional ingredients can be omitted',()=>{
 const p=sourced();assert.throws(()=>engine.proposal(p,proposal({action:'omit_optional',ingredientName:'Pasta'}),1),/optional/);
 const next=engine.proposal(p,proposal({action:'omit_optional',ingredientName:'Tomatoes'}),1);assert.equal(next.recipes.find(r=>r.id===next.menu[0].recipeId).ingredients.length,1);
});
test('options are previewable alternatives, not automatic mutations or nested workflows',()=>{
 const p=sourced(),change=proposal({action:'options',options:[{action:'replace',sourceRecipeId:'source-other',label:'Different recipe'},{action:'replace_product',ingredientName:'Pasta',productName:'Alternate pasta',label:'Alternate product'}]});
 assert.deepEqual(engine.proposal(p,change,1),p);change.options.push({action:'options',options:[]});assert.throws(()=>engine.proposal(p,change,1),/option action/);
});
test('meal patterns and vegetarian-day settings produce deterministic daily slots',()=>{
 const p=sourced();p.menu=[];p.preferences={...engine.preferences(p),days:3,mealPattern:'three-meals',vegetarianDays:2};const slots=engine.schedule(p);assert.equal(slots.length,9);assert.equal(slots[0].diet,'vegetarian');assert.equal(slots[6].diet,'mixed');
 const batch=engine.nextBatch(p),change=proposal({action:'generate',selections:batch.map(s=>({slotId:s.id,sourceRecipeId:'source-other'}))});const next=engine.proposal(p,change,1);assert.equal(next.menu.length,3);assert.equal(engine.nextBatch(next)[0].day,2);
 change.selections[0].slotId='unrequested';assert.throws(()=>engine.proposal(p,change,1),/requested meal/);
});

test('sourced recipe selections can map product names locally without AI quantities or prices',()=>{
 const p=sourced();p.menu=[];const choice={slotId:'day-1-dinner',sourceRecipeId:'source-other',mappings:[{ingredientName:'Pasta',productName:'Alternate pasta'}]};
 const next=engine.proposal(p,proposal({action:'generate',selections:[choice]}),1);assert.equal(engine.shopping(next).find(r=>r.name==='Pasta').cost,3);
 const chosen=next.recipes.find(r=>r.id===next.menu[0].recipeId);assert.deepEqual(chosen.steps,p.recipes[1].steps);assert.equal(chosen.ingredients[0].quantity,200);
 choice.mappings[0].quantity=50;assert.throws(()=>engine.proposal(p,proposal({action:'generate',selections:[choice]}),1),/name-based/);
});
