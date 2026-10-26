const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {JSDOM}=require(process.env.JSDOM_MODULE||'jsdom');const {TextEncoder}=require('node:util');
const root=path.join(__dirname,'..'),read=name=>fs.readFileSync(path.join(root,name),'utf8'),copy=x=>JSON.parse(JSON.stringify(x));
const settle=async()=>{await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));};
function sourced(){const p=JSON.parse(read('example.json'));p.recipes[0].source={kind:'imported',url:'https://recipes.example.test/pasta'};p.recipes.push({...copy(p.recipes[0]),id:'source-other',title:'Other source meal'});p.recipeSelection=['source-other'];return p;}
function backend(){let record={revision:0,value:null};return{get:()=>copy(record),set:args=>{if(args.expectedRevision!==record.revision)throw Error('Saved data changed in another view. Reload before saving.');record={revision:record.revision+1,value:copy(args.value)};return copy(record);}};}
async function view(store=backend(),workflow=''){
 const dom=new JSDOM('<!doctype html>'+read('planner.html'),{runScripts:'outside-only',url:'https://fixture.invalid'}),w=dom.window,sends=[],reads=[];w.TextEncoder=TextEncoder;
 const files={
  'catalogue-index.json':{schema:'agora.food-catalogue/v1',index:'catalogues/date/index.json'},
  'catalogues/date/index.json':{schema:'agora.food-catalogue/v1',storeId:'IE0206',fetchedAt:'2026-10-25T12:00:00Z',categories:[
    {category:'Pantry',subcategory:'Pasta',files:['catalogues/date/pasta.json']},
    {category:'Fresh',subcategory:'Fish',files:['catalogues/date/fish.json']}]},
  'catalogues/date/pasta.json':{products:[{id:'secret-product-id',title:'Pasta',price:98765.43,currency:'EUR',pack:{quantity:54321,unit:'g'},openFoodFacts:{provider:'Open Food Facts',attribution:'OFF contributors',environment:'staging',candidates:[{data:{ingredients_text:'PRIVATE-NUTRITION-MARKER',nutriscore_grade:'a',nutriments:{protein:76543}},fetchedAt:'2026-10-20T12:00:00Z'}]}}]},
  'catalogues/date/fish.json':{products:[{id:'fish-id',title:'Salmon',price:12345,currency:'EUR'}]}
 };
 w.Websandbox={connection:{remote:{storageGet:async()=>store.get(),storageSet:async args=>store.set(args),readTextFile:async({path})=>{reads.push(path);if(!files[path])throw Error('Missing file');return JSON.stringify(files[path]);},sendPrompt:async args=>{sends.push(copy(args));return true;}}}};
 w.agoraWorkflowInput=workflow;w.eval(read('engine.js')+'\nvar FOOD_EXAMPLE='+read('example.json')+';\n'+read('planner.js')+'\ninitFoodPlanner();');await settle();
 return {w,dom,store,sends,reads,files,el:id=>w.document.getElementById(id),close:()=>dom.window.close()};
}
test('ordinary controls persist locally and reopening restores the checklist with no AI call',async()=>{
 const store=backend(),v=await view(store);try{
  v.el('demo').click();await settle();assert.equal(store.get().revision,1);
  const box=v.el('items').querySelector('input');box.click();await settle();assert.equal(store.get().revision,2);assert.equal(v.sends.length,0);
  const reopened=await view(store);try{assert.equal(reopened.el('items').querySelector('input').checked,true);}finally{reopened.close();}
 }finally{v.close();}
});
test('category browsing reads only that category and AI receives names without product metadata or raw attachments',async()=>{
 const store=backend(),seed=sourced();seed.menu=[];store.set({expectedRevision:0,value:seed});const v=await view(store);try{
  v.el('load-catalogue').click();await settle();assert.deepEqual(v.reads,['catalogue-index.json','catalogues/date/index.json']);v.el('category').value='Pantry';v.el('category').dispatchEvent(new v.w.Event('change'));await settle();assert.deepEqual(v.reads,['catalogue-index.json','catalogues/date/index.json','catalogues/date/pasta.json']);
  assert.match(v.el('catalogue').textContent,/PRIVATE-NUTRITION-MARKER/);
  v.el('use-category').click();await settle();v.el('generate').click();await settle();assert.equal(v.sends.length,1);
  const call=v.sends[0];assert.equal(Object.hasOwn(call,'sourceFiles'),false);
  const payload=JSON.parse(call.text.substring(call.text.indexOf('\n')+1));assert.deepEqual(payload.productNames,['Pasta']);
  for(const secret of ['98765.43','54321','secret-product-id','Salmon','price','currency','pack','productId','PRIVATE-NUTRITION-MARKER','76543','nutriscore'])assert.equal(call.text.includes(secret),false,secret);
 }finally{v.close();}
});
test('two views reject stale writes and preserve the latest saved plan',async()=>{
 const store=backend(),a=await view(store),b=await view(store);try{
  a.el('demo').click();await settle();b.el('demo').click();await settle();assert.equal(store.get().revision,1);assert.match(b.el('save-status').textContent,/another view/);
 }finally{a.close();b.close();}
});
test('a completed AI proposal is previewed without saving until Apply is clicked',async()=>{
 const store=backend(),seed=sourced();store.set({expectedRevision:0,value:seed});
 const proposal={schema:'agora.food-proposal/v1',requestId:'request-1',baseRevision:1,action:'replace',slotId:'monday-dinner',sourceRecipeId:'source-other'};
 const v=await view(store,JSON.stringify(proposal));try{
  assert.equal(v.el('proposal').hidden,false);assert.equal(store.get().revision,1);assert.equal(v.sends.length,0);
  v.el('apply').click();await settle();assert.equal(store.get().revision,2);assert.equal(store.get().value.menu[0].recipeId,'source-other');assert.equal(v.sends.length,0);
 }finally{v.close();}
});

test('replacement handoff sends titles only by default and omits recipe bodies',async()=>{
 const store=backend();store.set({expectedRevision:0,value:sourced()});const v=await view(store);try{
  [...v.el('meals').querySelectorAll('button')].find(b=>b.textContent==='Open recipe').click();await settle();
  v.el('replace').click();await settle();const call=v.sends[0];const input=JSON.parse(call.text.split('\n').slice(1).join('\n'));
  assert.equal(input.recipe.title,"Example tomato pasta");assert.equal(Object.hasOwn(input.recipe,'ingredientNames'),false);assert.equal(Object.hasOwn(input.sourceRecipes[0],'ingredientNames'),false);assert.equal(Object.hasOwn(input.recipe,'ingredients'),false);
  assert.equal(Object.hasOwn(input.recipe,'steps'),false);assert.equal(call.text.includes('quantity'),false);assert.equal(call.text.includes('price'),false);
 }finally{v.close();}
});

test('shopping is nested under Menu and optional setup controls are initially collapsed',async()=>{
 const v=await view();try{assert.equal(v.el('shopping').closest('#menu'),v.el('menu'));assert.equal(v.el('optional-targets').open,false);assert.equal(v.el('change-options').hidden,true);v.el('menu-shopping').click();assert.equal(v.el('shopping').hidden,false);assert.equal(v.el('menu-recipes-view').hidden,true);}finally{v.close();}
});
test('planning without imported source recipes is blocked before any AI request',async()=>{
 const v=await view();try{v.el('generate').click();await settle();assert.equal(v.sends.length,0);assert.match(v.el('save-status').textContent,/source recipes/i);}finally{v.close();}
});

test('Ninja focus defaults on and can be independently disabled beside dietary preferences',async()=>{
 const v=await view();try{
  assert.equal(v.el('ninja-focus').checked,true);v.el('ninja-focus').checked=false;v.el('diet').value='vegan';v.el('save-preferences').click();await settle();
  assert.equal(v.store.get().value.preferences.equipment,'any');assert.equal(v.store.get().value.preferences.diet,'vegan');
  const reopened=await view(v.store);try{assert.equal(reopened.el('ninja-focus').checked,false);assert.equal(reopened.el('diet').value,'vegan');}finally{reopened.close();}
 }finally{v.close();}
});

test('optional ingredient check sends names for exactly one chosen recipe without amounts or steps',async()=>{
 const store=backend();store.set({expectedRevision:0,value:sourced()});const v=await view(store);try{
  v.el('ingredient-check').checked=true;v.el('ingredient-check-recipe').value='source-other';v.el('generate').click();await settle();
  const input=JSON.parse(v.sends[0].text.split('\n').slice(1).join('\n'));assert.equal(input.ingredientCheck.id,'source-other');assert.deepEqual(input.ingredientCheck.ingredientNames,['Pasta','Tomatoes']);assert.equal(Object.hasOwn(input.sourceRecipes[0],'ingredientNames'),false);assert.equal(JSON.stringify(input).includes('quantity'),false);assert.equal(JSON.stringify(input).includes('steps'),false);
 }finally{v.close();}
});
test('local paged search keeps unknown metadata by default and saved recipes survive reopening without AI',async()=>{
 const store=backend(),v=await view(store);try{
  v.files['recipes-index.json']={schema:'agora.source-recipes/v2',count:3,pages:['recipe-index/one.json']};
  v.files['recipe-index/one.json']={schema:'agora.recipe-page/v1',recipes:[
   {id:'a',title:'Lentil dinner',sourceUrl:'https://source.test/a',path:'recipes/a.json',dietTags:['vegan','vegetarian'],caloriesPerServing:400},
   {id:'b',title:'Unlabelled dinner',sourceUrl:'https://source.test/b',path:'recipes/b.json'},
   {id:'c',title:'Higher calorie dinner',sourceUrl:'https://source.test/c',path:'recipes/c.json',dietTags:['vegetarian'],caloriesPerServing:900}]};
  v.el('load-recipes').click();await settle();assert.deepEqual(v.reads,['recipes-index.json','recipe-index/one.json']);
  v.el('recipe-diet').value='vegan';v.el('recipe-max-calories').value='500';v.el('recipe-diet').dispatchEvent(new v.w.Event('input'));assert.match(v.el('recipe-status').textContent,/2 matches/);
  const row=[...v.el('source-recipes').querySelectorAll('article')].find(e=>e.textContent.includes('Lentil dinner'));[...row.querySelectorAll('button')].find(e=>e.textContent==='Save recipe').click();await settle();assert.deepEqual(store.get().value.savedRecipeTitles,['Lentil dinner']);assert.equal(store.get().value.recipes.length,0);assert.equal(v.sends.length,0);
  const reopened=await view(store);try{assert.deepEqual(reopened.store.get().value.savedRecipeTitles,['Lentil dinner']);assert.equal(reopened.sends.length,0);}finally{reopened.close();}
  v.el('recipe-unknown').checked=false;v.el('recipe-unknown').dispatchEvent(new v.w.Event('input'));assert.match(v.el('recipe-status').textContent,/1 matches/);assert.deepEqual(v.reads,['recipes-index.json','recipe-index/one.json']);
 }finally{v.close();}
});

test('a menu recipe can be saved and found without reopening the source library',async()=>{
 const store=backend();store.set({expectedRevision:0,value:sourced()});const v=await view(store);try{
  [...v.el('meals').querySelectorAll('button')].find(b=>b.textContent==='Open recipe').click();await settle();
  [...v.el('recipe').querySelectorAll('button')].find(b=>b.textContent==='Save recipe').click();await settle();
  const id=store.get().value.menu[0].recipeId;assert.deepEqual(store.get().value.savedRecipeTitles,[sourced().recipes[0].title]);
  const reopened=await view(store);try{reopened.el('recipe-saved').value='saved';reopened.el('recipe-saved').dispatchEvent(new reopened.w.Event('input'));assert.equal(reopened.el('source-recipes').querySelectorAll('article').length,1);assert.equal(reopened.reads.length,0);assert.equal(reopened.sends.length,0);}finally{reopened.close();}
 }finally{v.close();}
});

test('removing a library recipe from the shortlist releases its body while retaining its saved title',async()=>{
 const v=await view();try{
  const recipe=sourced().recipes[0];recipe.ingredients.forEach(i=>delete i.productId);v.files['recipes-index.json']={schema:'agora.source-recipes/v1',recipes:[{id:recipe.id,title:recipe.title,path:'recipes/source.json',sourceUrl:recipe.source.url}]};v.files['recipes/source.json']=recipe;
  v.el('load-recipes').click();await settle();
  const click=text=>[...v.el('source-recipes').querySelectorAll('button')].find(b=>b.textContent===text).click();
  click('Save recipe');await settle();click('Add to menu shortlist');await settle();assert.equal(v.store.get().value.recipes.length,1);
  click('Remove from menu shortlist');await settle();assert.equal(v.store.get().value.recipes.length,0);assert.deepEqual(v.store.get().value.savedRecipeTitles,[recipe.title]);assert.equal(v.sends.length,0);
 }finally{v.close();}
});

test('OFF refresh handoff sends the selected product name without its barcode or nutrition',async()=>{
 const store=backend(),seed=sourced();store.set({expectedRevision:0,value:seed});const v=await view(store);try{
  v.el('load-catalogue').click();await settle();v.el('category').value='Pantry';v.el('category').dispatchEvent(new v.w.Event('change'));await settle();
  [...v.el('catalogue').querySelectorAll('button')].find(b=>b.textContent==='Refresh saved OFF candidate').click();await settle();
  const call=v.sends[0],input=JSON.parse(call.text.split('\n').slice(1).join('\n'));assert.equal(input.action,'refresh_off');assert.equal(input.itemName,'Pasta');
  for(const field of ['PRIVATE-NUTRITION-MARKER','secret-product-id','98765.43','barcode','nutriscore','nutriments'])assert.equal(call.text.includes(field),false,field);
 }finally{v.close();}
});

function localLibrary(v,count=8){
 const summaries=[];
 for(let i=0;i<count;i++){
  const recipe=copy(sourced().recipes[0]);recipe.id='local-'+i;recipe.title='Source meal '+i;recipe.source.url='https://source.test/'+i;
  recipe.sourceIngredients=['PRIVATE full source ingredients'];recipe.steps=['PRIVATE cooking instructions'];recipe.ingredients.forEach(item=>delete item.productId);
  v.files['recipes/local-'+i+'.json']=recipe;
  summaries.push({id:recipe.id,title:recipe.title,path:'recipes/local-'+i+'.json',sourceUrl:recipe.source.url,category:'Dinner',dietTags:['vegetarian'],equipmentTags:['multi-cooker']});
 }
 v.files['recipes-index.json']={schema:'agora.source-recipes/v1',recipes:summaries};
 return summaries;
}
test('Go ahead loads a bounded source shortlist and submits once with optional sections untouched',async()=>{
 const v=await view();try{
  localLibrary(v);assert.equal(v.el('generate').textContent,'Go ahead');assert.equal(v.el('optional-settings').open,false);
  assert.equal(v.el('menu').hidden,true);assert.equal(v.el('ai-actions').hidden,true);
  v.el('generate').click();v.el('generate').click();await settle();
  assert.equal(v.sends.length,1);const input=JSON.parse(v.sends[0].text.split('\n').slice(1).join('\n'));
  assert.equal(input.action,'generate');assert.equal(input.sourceRecipes.length,6);assert.equal(input.targetSlots.length,1);
  assert.equal(input.preferences.equipment,'ninja-11-in-1');assert.equal(input.baseRevision,v.store.get().revision);
  assert.equal(v.reads.filter(path=>path.startsWith('recipes/')).length,6);assert.equal(v.reads.some(path=>path.includes('catalogue')),false);
  assert.equal(v.sends[0].text.includes('PRIVATE'),false);assert.ok(v.sends[0].text.length<3900);
  assert.equal(v.el('generate').disabled,true);assert.match(v.el('ai-status').textContent,/Sent/);
 }finally{v.close();}
});
test('Go ahead validates required choices before reading files or calling AI',async()=>{
 const v=await view();try{localLibrary(v);v.el('plan-days').value='0';v.el('generate').click();await settle();
  assert.equal(v.sends.length,0);assert.equal(v.reads.length,0);assert.equal(v.store.get().revision,0);
  assert.match(v.el('ai-status').textContent,/Not sent/);assert.equal(v.el('generate').disabled,false);
 }finally{v.close();}
});
test('a missing library gives the reference-folder recovery and keeps Go ahead retryable',async()=>{
 const v=await view();try{v.el('generate').click();await settle();assert.equal(v.sends.length,0);
  assert.match(v.el('save-status').textContent,/Reference folder/);assert.equal(v.el('generate').disabled,false);
  localLibrary(v);v.el('generate').click();await settle();assert.equal(v.sends.length,1);
 }finally{v.close();}
});
test('review shows meal titles instead of JSON and Apply preserves the remaining days',async()=>{
 const store=backend(),seed=sourced();seed.menu=[];store.set({expectedRevision:0,value:seed});
 const proposal={schema:'agora.food-proposal/v1',requestId:'day-one',baseRevision:1,action:'generate',selections:[{slotId:'day-1-dinner',sourceRecipeId:'source-other'}]};
 const v=await view(store,JSON.stringify(proposal));try{
  assert.equal(v.el('setup').hidden,true);assert.equal(v.el('optional-settings').hidden,true);
  assert.match(v.el('suggestion').textContent,/Day 1 dinner: Other source meal/);assert.equal(v.el('suggestion').querySelector('pre'),null);
  assert.equal(store.get().revision,1);v.el('apply').click();await settle();
  assert.equal(store.get().value.menu.length,1);assert.equal(v.el('setup').hidden,false);assert.equal(v.el('setup').open,true);
  assert.match(v.el('planning-progress').textContent,/Day 2 dinner/);assert.equal(v.el('menu').hidden,false);
 }finally{v.close();}
});
test('inactive cards explain how to reopen instead of presenting disabled setup',async()=>{
 const store={get:()=>{throw Error('This interface is no longer active');}},v=await view(store);try{
  assert.equal(v.el('inactive-help').hidden,false);assert.equal(v.el('setup').hidden,true);assert.equal(v.el('optional-settings').hidden,true);
  assert.match(v.el('inactive-help').textContent,/\/food/);v.el('generate').click();await settle();assert.equal(v.sends.length,0);
 }finally{v.close();}
});
test('automatic dinner choices skip labelled desserts and retain unknown diet metadata',async()=>{
 const v=await view();try{
  const entries=localLibrary(v);entries[0].category='Dessert';entries[1].dietTags=[];
  v.el('diet').value='vegan';v.el('generate').click();await settle();
  assert.equal(v.sends.length,1);const input=JSON.parse(v.sends[0].text.split('\n').slice(1).join('\n'));
  assert.deepEqual(input.sourceRecipes.map(r=>r.id),['local-1']);
 }finally{v.close();}
});
test('optional settings are after Go ahead and opening them never submits',async()=>{
 const v=await view();try{
  assert.ok(v.el('generate').compareDocumentPosition(v.el('optional-settings'))&v.w.Node.DOCUMENT_POSITION_FOLLOWING);
  for(const id of ['recipe-sources','lidl-options','planner-tools','optional-targets'])assert.equal(v.el(id).open,false);
  v.el('optional-settings').open=true;v.el('recipe-sources').open=true;await settle();assert.equal(v.sends.length,0);
 }finally{v.close();}
});
