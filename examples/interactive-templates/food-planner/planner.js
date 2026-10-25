function initFoodPlanner() {
  const api=Websandbox.connection.remote, key='food-planner.v1', el=id=>document.getElementById(id);
  let record={revision:0,value:null}, plan=FoodPlanner.empty(), ready=false, busy=false, sent=false, selected=null, pending=null, undo=null, changeTarget=null, offTarget=null, browseToken=0, setupDirty=false;
  let index=null,page=0,products=[],section=null,recipeEntries=[],recipePage=0,recipeLoadToken=0,recipeMatchCount=0;
  const label=(tag,value)=>{const node=document.createElement(tag);node.textContent=value;return node;};
  const error=failure=>{el('save-status').textContent=failure.message||String(failure);};
  const action=(text,fn)=>{const node=label('button',text);node.type='button';node.onclick=()=>Promise.resolve().then(fn).catch(error);return node;};
  function controls(){for(const button of document.querySelectorAll('button,input,select'))button.disabled=busy||!ready;
    el('reload').disabled=busy;el('demo').disabled=busy||!ready||plan.menu.length>0;
    el('generate').disabled=busy||!ready||sent||FoodPlanner.nextBatch(plan).length===0;el('replace').disabled=busy||!ready||sent||!selected;el('change-item').disabled=busy||!ready||sent||!changeTarget;
    el('catalogue-prev').disabled=busy||!index||page===0;el('catalogue-next').disabled=busy||!index||(!section||page+1>=section.files.length);el('recipes-prev').disabled=busy||recipePage===0;el('recipes-next').disabled=busy||(recipePage+1)*20>=recipeMatchCount;updateSetupLabels();}
  function savedTitles(value=plan){
    const known=[...value.recipes,...recipeEntries];return [...new Set([...(value.savedRecipeTitles||[]),...(value.savedRecipeIds||[]).map(id=>known.find(r=>r.id===id)?.title).filter(Boolean)])];
  }
  function toggleSavedTitle(next,title,saved){
    next.savedRecipeTitles=saved?savedTitles(next).filter(t=>t!==title):[...new Set([...savedTitles(next),title])];
    if((next.savedRecipeIds||[]).some(id=>![...next.recipes,...recipeEntries].some(r=>r.id===id)))throw Error('Load the local recipe library to resolve older bookmarks before saving.');
    delete next.savedRecipeIds;
    return next;
  }
  function pruneLibraryBodies(next){
    next.recipes=next.recipes.filter(r=>next.menu.some(s=>s.recipeId===r.id)||(next.recipeSelection||[]).includes(r.id)||!recipeEntries.some(e=>e.id===r.id&&e.path));return next;
  }
  async function commit(next,rememberUndo=true){
    if(busy||!ready)throw Error('Wait for the current save');
    FoodPlanner.validate(next);busy=true;controls();el('save-status').textContent='Saving…';
    try {
      const saved=await api.storageSet({key,expectedRevision:record.revision,value:next});
      if(rememberUndo)undo=FoodPlanner.clone(plan);
      record=saved;plan=FoodPlanner.validate(saved.value);el('save-status').textContent='Saved · revision '+record.revision;render();
    }catch(failure){error(failure);throw failure;}finally{busy=false;controls();}
  }
  async function reload(){
    busy=true;setupDirty=false;controls();
    try {record=await api.storageGet({key});plan=record.value===null?FoodPlanner.empty():FoodPlanner.validate(record.value);ready=true;undo=null;
      el('save-status').textContent=record.value===null?'No saved menu yet':'Saved · revision '+record.revision;render();}
    catch(failure){ready=false;error(failure);}finally{busy=false;controls();}
  }
  function recipeView(){
    const root=el('recipe');root.replaceChildren();const slot=plan.menu.find(s=>s.id===selected);root.hidden=!slot;if(!slot)return;
    const recipe=plan.recipes.find(r=>r.id===slot.recipeId);root.append(label('h2',recipe.title));
    if(recipe.source?.kind==='imported'){const saved=savedTitles().includes(recipe.title);root.append(action(saved?'Unsave recipe':'Save recipe',async()=>{const next=FoodPlanner.clone(plan);toggleSavedTitle(next,recipe.title,saved);await commit(next);}));}
    if(recipe.source?.url)root.append(action('Open original recipe source',()=>api.openLink({url:recipe.source.url})));
    if(recipe.sourceIngredients){const original=document.createElement('details');original.append(label('summary','Original source ingredients'),label('pre',recipe.sourceIngredients.join('\n')));root.append(original);}
    if(recipe.sourceNutrition){const nutrition=document.createElement('details');nutrition.append(label('summary','Nutrition supplied by the recipe source'),label('pre',JSON.stringify(recipe.sourceNutrition,null,2)));root.append(nutrition);}
    if(recipe.adaptationNotes?.length)root.append(label('p',recipe.adaptationNotes.join(' ')));
    const field=label('label','Servings '),input=document.createElement('input');input.type='number';input.min='1';input.max='24';input.value=slot.servings;field.append(input);root.append(field);
    root.append(action('Save servings',async()=>{const next=FoodPlanner.clone(plan);next.menu.find(s=>s.id===selected).servings=Number(input.value);await commit(next);}));
    const ingredients=document.createElement('ul');
    recipe.ingredients.forEach((item,i)=>{
      const row=document.createElement('li');row.append(label('span',`${item.quantity===null?'Quantity not parsed':Math.round(item.quantity*slot.servings/recipe.servings*1000)/1000} ${item.unit==='unspecified'?'':item.unit} ${item.name}`));
      const quantity=document.createElement('input');quantity.type='number';quantity.step='any';quantity.min='.001';quantity.value=item.quantity===null?'':item.quantity;quantity.setAttribute('aria-label','Base quantity for '+item.name);const base=label('label','Base quantity for '+recipe.servings+' servings ');base.append(quantity);row.append(base);
      const mapping=document.createElement('select');mapping.setAttribute('aria-label','Product for '+item.name);mapping.append(new Option('No verified product selected',''));
      plan.products.forEach(p=>mapping.append(new Option(p.title,p.id)));mapping.value=item.productId||'';row.append(mapping);
      row.append(action('Save ingredient',async()=>{const next=FoodPlanner.clone(plan),entry=next.recipes.find(r=>r.id===recipe.id).ingredients[i];entry.quantity=Number(quantity.value);entry.productId=mapping.value||null;next.recipes.find(r=>r.id===recipe.id).adaptationNotes=['Ingredient quantity or product mapping edited locally; see the original source ingredients.'];await commit(next);}));ingredients.append(row);
    });root.append(ingredients);const steps=document.createElement('ol');recipe.steps.forEach(step=>steps.append(label('li',step)));root.append(steps);
  }
  function render(){
    el('data-source').textContent=plan.provenance?.kind==='example'?'Example data only · not live Lidl products or prices.':'Prices and pack sizes stay local; AI receives product names only.';
    const prefs=FoodPlanner.preferences(plan),next=FoodPlanner.nextBatch(plan);
    if(!setupDirty){el('plan-days').value=prefs.days;el('plan-servings').value=prefs.servings;el('meal-pattern').value=prefs.mealPattern;el('diet').value=prefs.diet;el('ninja-focus').checked=prefs.equipment==='ninja-11-in-1';el('vegetarian-days').value=prefs.vegetarianDays;
    el('budget-enabled').checked=prefs.budget!==null;el('budget').value=prefs.budget===null?75:prefs.budget;el('budget-value').textContent=el('budget').value;el('calorie-enabled').checked=prefs.calories!==null;el('calories').value=prefs.calories===null?700:prefs.calories;}el('setup').open=plan.menu.length===0;updateSetupLabels();showRecipes();
    el('planning-progress').textContent=plan.menu.length+' meals saved. '+(next.length?'Next request: '+next.map(s=>s.label).join(', ')+'.':'All requested meal slots are planned.');
    const root=el('meals');root.replaceChildren();
    if(!plan.menu.length)root.append(label('p','Start with an example or request a menu.'));
    plan.menu.forEach(slot=>{const recipe=plan.recipes.find(r=>r.id===slot.recipeId),card=document.createElement('article');
      card.append(label('h3',slot.label),label('p',recipe.title+' · '+slot.servings+' servings'),action('Open recipe',()=>{selected=slot.id;recipeView();controls();}));
      root.append(card);
    });recipeView();
    const rows=FoodPlanner.shopping(plan),items=el('items');items.replaceChildren();
    rows.forEach(row=>{const line=document.createElement('label'),box=document.createElement('input');box.type='checkbox';box.checked=row.checked;box.setAttribute('aria-label','Purchased '+row.name);
      const title=label('span',`${row.quantity===null?'Quantity unknown':row.quantity} ${row.unit==='unspecified'?'':row.unit} ${row.name}`);if(row.checked)title.className='purchased';line.append(box,title);
      line.append(label('small',row.cost===null?' · price unknown':` · €${row.cost.toFixed(2)} (${row.packs} pack(s))`));
      line.append(action('View product',()=>deepDive(row)),action('Change item',()=>chooseChange(row)));
      box.onchange=()=>{const next=FoodPlanner.clone(plan);if(box.checked)next.checked[row.key]=true;else delete next.checked[row.key];commit(next).catch(()=>{box.checked=row.checked;});};items.append(line);
    });
    const priced=rows.filter(r=>r.cost!==null),sum=priced.reduce((total,r)=>total+r.cost,0);
    el('total').textContent=`Known-price subtotal €${sum.toFixed(2)} · ${rows.length-priced.length} unpriced item(s). Saved catalogue prices; not a live checkout total.`+(prefs.budget===null?'':sum>prefs.budget?' Over your €'+prefs.budget+' target; review alternatives.':' Budget target €'+prefs.budget+'; unpriced items prevent confirming the full total.');
    el('extras').replaceChildren();plan.extras.forEach((item,i)=>el('extras').append(action('Remove '+item.name,async()=>{const next=FoodPlanner.clone(plan);next.extras.splice(i,1);await commit(next);})));
    if(undo)el('extras').append(action('Undo last saved edit',async()=>{const prior=undo;await commit(prior,false);undo=null;render();}));
    el('proposal').hidden=!pending;
    el('suggestion').replaceChildren();
    if(pending&&pending.baseRevision!==record.revision){pending=null;el('proposal').hidden=true;el('save-status').textContent='Saved data changed; request a fresh suggestion.';}
    if(pending){
      if(pending.action==='options') {
        el('apply').hidden=true;
        pending.options.forEach(option=>el('suggestion').append(action(option.label||option.productName||plan.recipes.find(r=>r.id===option.sourceRecipeId)?.title||'Review option',()=>{
          pending={...option,schema:pending.schema,requestId:pending.requestId,baseRevision:pending.baseRevision,slotId:pending.slotId};render();
        })));
      } else {
        el('apply').hidden=false;
        const preview=FoodPlanner.proposal(plan,pending,record.revision),rows=FoodPlanner.shopping(preview),known=rows.filter(r=>r.cost!==null);
        el('suggestion').append(label('p',pending.action==='replace_product'?'Alternate product: '+pending.productName:pending.action==='replace'?'Suggested source recipe: '+plan.recipes.find(r=>r.id===pending.sourceRecipeId)?.title:'Suggested menu'),
          label('pre',JSON.stringify(pending.action==='replace'?{sourceRecipe:plan.recipes.find(r=>r.id===pending.sourceRecipeId)?.title,source:plan.recipes.find(r=>r.id===pending.sourceRecipeId)?.source?.url}:pending.action==='replace_product'?{ingredient:pending.ingredientName,product:pending.productName}:pending.action==='omit_optional'?{omit:pending.ingredientName}:pending.selections,null,2)),
          label('p','After applying: known-price subtotal €'+known.reduce((n,r)=>n+r.cost,0).toFixed(2)+' · '+(rows.length-known.length)+' unpriced item(s).'));
      }
    }
    controls();
  }
  function showTab(tab){['menu','products'].forEach(id=>el(id).hidden=id!==tab);document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tab===tab)));}
  function menuSection(shopping){showTab('menu');el('menu-recipes-view').hidden=shopping;el('shopping').hidden=!shopping;}
  function chooseChange(row){
    const choices=plan.menu.filter(slot=>plan.recipes.find(r=>r.id===slot.recipeId).ingredients.some(i=>row.productId?i.productId===row.productId:i.name.trim().toLowerCase()===row.name.trim().toLowerCase()));
    if(!choices.length)throw Error('This is an extra item. Remove it and choose another product locally.');
    changeTarget=row.name;el('change-options').hidden=false;el('change-target').textContent='Change '+row.name+' for the selected meal. Other meals remain unchanged.';
    el('change-meal').replaceChildren();choices.forEach(slot=>el('change-meal').append(new Option(slot.label,slot.id)));
    selected=choices.some(slot=>slot.id===selected)?selected:choices[0].id;el('change-meal').value=selected;controls();
  }
  function productDetails(p){
    const details=document.createElement('div');details.append(label('h3',p.title),label('p',p.price==null?'Price unknown':`${p.currency||''} ${p.price} per pack`));
    if(p.pack)details.append(label('p',`Pack size: ${p.pack.quantity} ${p.pack.unit}`));
    details.append(action('Refresh saved OFF candidate',()=>{offTarget=p.title;return ask('refresh_off');}));
    const off=p.openFoodFacts;
    if(!off)details.append(label('p','No Open Food Facts results imported for this product.'));
    else {
      details.append(label('p',off.attribution+' · '+(off.environment||'environment unknown')+' · name-based candidates, not verified Lidl identities.'));
      if(!off.candidates?.length)details.append(label('p','No candidate in the saved sample. Missing data is unknown.'));
      for(const candidate of off.candidates||[]){
        const info=candidate.data||{};
        details.append(label('h3',info.product_name||'Unnamed candidate'),label('p','Unverified candidate · retrieved '+(candidate.fetchedAt||'unknown')),
          label('p','Candidate Nutri-Score: '+(info.nutriscore_grade||'unknown')+' · NOVA: '+(info.nova_group||'unknown')),
          label('p','Ingredients: '+(info.ingredients_text||info.ingredients_text_en||'unknown')),
          label('p','Allergens: '+((info.allergens_tags||[]).join(', ')||'unknown')),
          label('p','Conflicts: '+((candidate.conflicts||[]).join(', ')||'none detected; still unverified')),
          label('pre',JSON.stringify(info.nutriments||{},null,2)));
      }
    }
    return details;
  }
  async function deepDive(row){
    const product=plan.products.find(p=>p.id===row.productId);
    el('product-deep-dive').replaceChildren();el('product-detail').hidden=false;showTab('products');
    if(!product){el('product-deep-dive').append(label('p',row.name+' has no selected catalogue product. Choose one from a relevant category.'));return;}
    let full=product;
    if(product.cataloguePath){const page=JSON.parse(await api.readTextFile({path:relativeFile(product.cataloguePath)}));full=page.products.find(p=>p.id===product.id)||product;}
    el('product-deep-dive').append(productDetails(full));
  }
  async function selectNames(){
    if(!section)throw Error('Choose a catalogue section first');
    const next=FoodPlanner.clone(plan),ids=new Set(next.aiSelection||[]);
    for(const p of products){if(!next.products.some(item=>item.id===p.id))next.products.push({id:p.id,title:p.title,brand:p.brand||'',price:p.price,currency:p.currency,pack:p.pack,cataloguePath:section.files[page]});ids.add(p.id);}
    next.aiSelection=[...ids];await commit(next);el('ai-sections').textContent=next.aiSelection.length+' product names selected; metadata stays local.';
  }
  function showProducts(){
    const root=el('catalogue');root.replaceChildren();const needle=el('product-search').value.trim().toLowerCase();
    products.filter(p=>(p.title+' '+(p.brand||'')).toLowerCase().includes(needle)).forEach(p=>{
      const card=document.createElement('article');card.append(label('h3',p.title),label('p',p.price==null?'Price unknown':`${p.currency||''} ${p.price} per pack`));
      const details=document.createElement('details');details.append(label('summary','Product and Open Food Facts details'),productDetails(p));card.append(details);
      card.append(action('Add one pack to list',async()=>{const next=FoodPlanner.clone(plan);if(!next.products.some(item=>item.id===p.id))next.products.push({id:p.id,title:p.title,brand:p.brand||'',price:p.price,currency:p.currency,pack:p.pack,cataloguePath:section.files[page]});next.extras.push({name:p.title,quantity:1,unit:'pack',productId:p.id});await commit(next);}));root.append(card);
    });controls();
  }
  function relativeFile(path) {
    if(typeof path!=='string'||path.startsWith('/')||path.split('/').some(p=>!p||p==='.'||p==='..')||/[\\\x00-\x1f]/.test(path))throw Error('Invalid catalogue file path');
    return path;
  }
  async function loadPage(target){
    if(!section||!Number.isInteger(target)||target<0||target>=section.files.length)return;
    const token=++browseToken,chosen=section,path=relativeFile(chosen.files[target]);
    const data=JSON.parse(await api.readTextFile({path}));if(token!==browseToken||section!==chosen)return;if(!Array.isArray(data.products)||data.products.length>100)throw Error('Invalid catalogue page');
    products=data.products;page=target;
    const age=Math.floor((Date.now()-Date.parse(index.fetchedAt))/86400000);
    el('catalogue-status').textContent=`Store ${index.storeId||'unknown'} · snapshot ${index.fetchedAt||'unknown'} · ${Number.isFinite(age)?age+' days old'+(age>3?' · refresh recommended':''):'age unknown'} · ${index.complete===true?'complete snapshot':'partial or completeness unverified'} · ${section.category} / ${section.subcategory} · page ${page+1}/${section.files.length}.`;
    showProducts();
  }
  function chooseCategory(){
    const root=el('subcategory');root.replaceChildren();
    index.categories.filter(s=>s.category===el('category').value).forEach(s=>root.append(new Option(s.subcategory,s.subcategory)));
    chooseSubcategory().catch(error);
  }
  async function chooseSubcategory(){
    section=index.categories.find(s=>s.category===el('category').value&&s.subcategory===el('subcategory').value);
    await loadPage(0);
  }
  async function loadCatalogue(){
    const pointer=JSON.parse(await api.readTextFile({path:'catalogue-index.json'}));
    if(pointer.schema!=='agora.food-catalogue/v1')throw Error('Invalid catalogue pointer');
    const loaded=JSON.parse(await api.readTextFile({path:relativeFile(pointer.index)}));
    if(loaded.schema!=='agora.food-catalogue/v1'||!Array.isArray(loaded.categories)||loaded.categories.length>500)throw Error('Invalid catalogue index');
    for(const s of loaded.categories){if(!Array.isArray(s.files))throw Error('Invalid category files');s.files.forEach(relativeFile);}
    index=loaded;products=[];section=null;++browseToken;el('ai-sections').textContent=(plan.aiSelection||[]).length+' product names selected for AI';
    const root=el('category');root.replaceChildren(new Option('Choose a category',''));[...new Set(index.categories.map(s=>s.category))].forEach(name=>root.append(new Option(name,name)));
    const sub=el('subcategory');sub.replaceChildren();index.categories.filter(s=>s.category===root.value).forEach(s=>sub.append(new Option(s.subcategory,s.subcategory)));
    el('catalogue').replaceChildren();el('catalogue-status').textContent='Choose a category and subcategory to load products.';controls();
  }
  function showRecipes(){
    const query=el('recipe-search').value.trim().toLowerCase(),diet=el('recipe-diet').value,max=Number(el('recipe-max-calories').value)||null,unknown=el('recipe-unknown').checked,mode=el('recipe-saved').value;
    if(mode==='saved'){showSavedTitles(query);return;}
    const focus=el('ninja-focus').checked;
    const available=[...recipeEntries];for(const recipe of plan.recipes){if(!available.some(e=>e.id===recipe.id)&&recipe.source?.kind==='imported'&&(savedTitles().includes(recipe.title)||(plan.recipeSelection||[]).includes(recipe.id)))available.push({id:recipe.id,title:recipe.title,sourceUrl:recipe.source.url,dietTags:recipe.dietTags,caloriesPerServing:recipe.caloriesPerServing,equipmentTags:recipe.equipmentTags});}
    const matches=available.filter(e=>{
      const tags=e.dietTags||[],cal=e.caloriesPerServing;
      return (!query||(e.title+' '+(Array.isArray(e.category)?e.category.join(' '):e.category||'')).toLowerCase().includes(query))&&
        (mode!=='shortlist'||(plan.recipeSelection||[]).includes(e.id))&&
        (diet==='any'||tags.includes(diet)||unknown&&!tags.length)&&(!max||(cal>0?cal<=max:unknown));
    }).sort((a,b)=>{
      const rank=e=>(e.equipmentTags||[]).includes('ninja-11-in-1')?3:(e.equipmentTags||[]).includes('multi-cooker')?2:(e.equipmentTags||[]).includes('ninja')?1:0;
      return (focus?rank(b)-rank(a):0)||a.title.localeCompare(b.title);
    });
    recipeMatchCount=matches.length;recipePage=Math.min(recipePage,Math.max(0,Math.ceil(matches.length/20)-1));
    const root=el('source-recipes');root.replaceChildren();
    for(const entry of matches.slice(recipePage*20,(recipePage+1)*20)){
      const row=document.createElement('article'),saved=savedTitles().includes(entry.title),shortlisted=(plan.recipeSelection||[]).includes(entry.id);
      row.append(label('h3',entry.title),label('p',(entry.dietTags||[]).join(', ')||'Diet not labelled'),label('p',entry.caloriesPerServing>0?entry.caloriesPerServing+' kcal per source serving':'Calories not provided'),label('p',(entry.equipmentTags||[]).includes('ninja-11-in-1')?'Source mentions Ninja 11-in-1':(entry.equipmentTags||[]).includes('multi-cooker')?'Ninja multi-cooker recipe · check your model':(entry.equipmentTags||[]).includes('ninja')?'Ninja source · equipment varies':'Equipment not labelled'),
        action(saved?'Unsave recipe':'Save recipe',async()=>{const next=FoodPlanner.clone(plan);toggleSavedTitle(next,entry.title,saved);await commit(pruneLibraryBodies(next));showRecipes();}),
        action(shortlisted?'Remove from menu shortlist':'Add to menu shortlist',async()=>{
          const recipe=shortlisted?null:await readRecipe(entry),next=FoodPlanner.clone(plan);
          if(shortlisted)next.recipeSelection=(next.recipeSelection||[]).filter(id=>id!==entry.id);
          else {if(!next.recipes.some(r=>r.id===recipe.id))next.recipes.push(recipe);next.recipeSelection=[...new Set([...(next.recipeSelection||[]),entry.id])];}
          await commit(pruneLibraryBodies(next));showRecipes();
        }),action('Inspect source recipe',async()=>{
          const recipe=await readRecipe(entry);const details=document.createElement('details');details.open=true;details.append(label('summary','Source recipe'),label('p',entry.sourceUrl),label('pre',(recipe.sourceIngredients||recipe.ingredients.map(i=>i.original||i.name)).join('\n')),label('pre',recipe.steps.join('\n\n')));row.append(details);
        }));root.append(row);
    }
    el('recipe-status').textContent=matches.length+' matches · page '+(recipePage+1)+' · '+(plan.recipeSelection||[]).length+' shortlisted · saved recipes stay in this chat. '+(focus?'Ninja multi-cooker recipes shown first.':'');
    el('recipes-prev').disabled=recipePage===0;el('recipes-next').disabled=(recipePage+1)*20>=matches.length;
    const check=el('ingredient-check-recipe'),previous=check.value;check.replaceChildren();plan.recipes.filter(r=>(plan.recipeSelection||[]).includes(r.id)||plan.menu.some(s=>s.recipeId===r.id)).forEach(r=>check.append(new Option(r.title,r.id)));if([...check.options].some(o=>o.value===previous))check.value=previous;
  }
  function showSavedTitles(query){
    const titles=savedTitles().filter(title=>!query||title.toLowerCase().includes(query)).sort((a,b)=>a.localeCompare(b));recipeMatchCount=titles.length;recipePage=Math.min(recipePage,Math.max(0,Math.ceil(titles.length/20)-1));
    const root=el('source-recipes');root.replaceChildren();
    for(const title of titles.slice(recipePage*20,(recipePage+1)*20)){
      const row=document.createElement('article');row.append(label('h3',title),action('Unsave recipe',async()=>{const next=toggleSavedTitle(FoodPlanner.clone(plan),title,true);await commit(pruneLibraryBodies(next));showRecipes();}),action('Find in local library',async()=>{el('recipe-search').value=title;el('recipe-saved').value='all';el('recipe-diet').value='any';el('recipe-max-calories').value='';await loadRecipes();}));root.append(row);
    }
    el('recipe-status').textContent=titles.length+' saved titles · page '+(recipePage+1)+' · stored in this chat. Find in the library to choose a source recipe.';
    el('recipes-prev').disabled=recipePage===0;el('recipes-next').disabled=(recipePage+1)*20>=titles.length;
  }
  async function readRecipe(entry){
    const stored=plan.recipes.find(r=>r.id===entry.id&&r.source?.kind==='imported'&&r.source.url===entry.sourceUrl);if(stored)return FoodPlanner.clone(stored);
    const path=relativeFile(entry.path);if(!path.startsWith('recipes/'))throw Error('Invalid source recipe path');
    const recipe=JSON.parse(await api.readTextFile({path}));if(recipe.id!==entry.id||recipe.source?.kind!=='imported'||recipe.source.url!==entry.sourceUrl)throw Error('Source recipe identity changed');return recipe;
  }
  async function loadRecipes(){
    const token=++recipeLoadToken,index=JSON.parse(await api.readTextFile({path:'recipes-index.json'}));let entries=[];
    if(index.schema==='agora.source-recipes/v1'&&Array.isArray(index.recipes)&&index.recipes.length<=200)entries=index.recipes;
    else if(index.schema==='agora.source-recipes/v2'&&Array.isArray(index.pages)&&index.pages.length<=200){
      for(const file of index.pages){const path=relativeFile(file);if(!path.startsWith('recipe-index/'))throw Error('Invalid recipe index page');const page=JSON.parse(await api.readTextFile({path}));if(page.schema!=='agora.recipe-page/v1'||!Array.isArray(page.recipes)||page.recipes.length>60)throw Error('Invalid recipe summary page');entries.push(...page.recipes);}
      if(entries.length!==index.count)throw Error('Incomplete local recipe index');
    }else throw Error('Invalid recipe source index');
    if(token!==recipeLoadToken)return;
    if(entries.some(e=>!e||typeof e.title!=='string'||typeof e.id!=='string'||typeof e.sourceUrl!=='string'))throw Error('Invalid recipe summary');
    recipeEntries=entries;recipePage=0;showRecipes();
  }
  async function ask(actionName){
    if(sent||busy||!ready)return;
    const preferences=readSetup();if(JSON.stringify(preferences)!==JSON.stringify(FoodPlanner.preferences(plan))){const next=FoodPlanner.clone(plan);next.preferences=preferences;await commit(next);setupDirty=false;}
    const slot=plan.menu.find(s=>s.id===selected);
    const input={schema:'agora.food-request/v1',action:actionName,requestId:'request-'+Date.now(),baseRevision:record.revision,request:el('request').value.trim(),itemName:actionName==='refresh_off'?offTarget:changeTarget,sourceUrl:actionName==='import_source'?el('recipe-url').value.trim():null,sourceRecipes:plan.recipes.filter(r=>(plan.recipeSelection||[]).includes(r.id)&&r.source?.kind==='imported').map(r=>({id:r.id,title:r.title,dietTags:r.dietTags||[]})),preferences:FoodPlanner.preferences(plan),targetSlots:actionName==='generate'?FoodPlanner.nextBatch(plan):[],
      productNames:[...new Set(plan.products.filter(p=>(plan.aiSelection||[]).includes(p.id)).map(p=>p.title))],slot:slot?{id:slot.id,label:slot.label}:null,recipe:slot?{id:slot.recipeId,title:plan.recipes.find(r=>r.id===slot.recipeId).title}:null};
    if(el('ingredient-check').checked){const recipe=plan.recipes.find(r=>r.id===el('ingredient-check-recipe').value);if(!recipe)throw Error('Choose a recipe for the optional ingredient check');input.ingredientCheck={id:recipe.id,ingredientNames:recipe.ingredients.map(i=>i.name),optionalIngredientNames:recipe.ingredients.filter(i=>i.optional).map(i=>i.name)};}
    if(['generate','replace','modify_recipe','options'].includes(actionName)&&!input.sourceRecipes.length)throw Error('Import and select suitable source recipes in Menu setup first. No AI request was made.');
    if(actionName==='import_source'){let url;try{url=new URL(input.sourceUrl);}catch(_){throw Error('Enter an HTTPS recipe source URL first. No AI request was made.');}if(url.protocol!=='https:'||url.username||url.password)throw Error('Use an HTTPS recipe source URL without credentials');}
    const text='For this NEW request invoke food-planner.md as a fresh root Skill (omit parent), then follow its proposal/completion contract. Use only this latest request, not an earlier workflow proposal.\n'+JSON.stringify(input);
    if(text.length>3900)throw Error('Selection is too large for one AI handoff; reduce the product shortlist or recipe');
    el('generate').disabled=true;el('replace').disabled=true;
    try {const result=await api.sendPrompt({text});if(result===false||result?.ok===false)throw Error('Request was not sent');sent=true;el('ai-status').textContent='Request sent · continue in chat. The result opens a fresh review card.';}
    finally{controls();}
  }
  document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>showTab(button.dataset.tab));
  el('menu-recipes').onclick=()=>menuSection(false);el('menu-shopping').onclick=()=>menuSection(true);
  el('back-to-shopping').onclick=()=>menuSection(true);
  el('cancel-change').onclick=()=>{changeTarget=null;el('change-options').hidden=true;controls();};
  el('change-meal').onchange=()=>{selected=el('change-meal').value;};
  el('change-item').onclick=()=>ask(el('change-mode').value).catch(error);
  function updateSetupLabels(){
    const vegan=el('diet').value!=='mixed';el('vegetarian-label').hidden=vegan;el('vegetarian-days').disabled=vegan||busy;el('vegetarian-days').max=el('plan-days').value;
    el('vegetarian-help').textContent=vegan?'Every planned day follows the selected diet.':'For mixed menus, the first selected number of days are vegetarian.';
    el('calories').disabled=!el('calorie-enabled').checked||busy;el('budget').disabled=!el('budget-enabled').checked||busy;el('budget-value').textContent=el('budget').value;
    el('calorie-label').textContent=el('meal-pattern').value==='dinner'?'Target calories per dinner per person':'Target calories per day per person (all three meals)';
  }
  ['diet','meal-pattern','plan-days','plan-servings','vegetarian-days','budget-enabled','budget','calorie-enabled','calories','ninja-focus'].forEach(id=>el(id).oninput=()=>{setupDirty=true;updateSetupLabels();});
  function readSetup(){return {days:Number(el('plan-days').value),servings:Number(el('plan-servings').value),mealPattern:el('meal-pattern').value,diet:el('diet').value,vegetarianDays:Math.min(Number(el('vegetarian-days').value),Number(el('plan-days').value)),budget:el('budget-enabled').checked?Number(el('budget').value):null,calories:el('calorie-enabled').checked?Number(el('calories').value):null,equipment:el('ninja-focus').checked?'ninja-11-in-1':'any'};}
  el('save-preferences').onclick=()=>{const next=FoodPlanner.clone(plan);next.preferences=readSetup();commit(next).then(()=>{setupDirty=false;}).catch(error);};
  el('reload').onclick=reload;
  el('demo').onclick=()=>commit(FoodPlanner.clone(FOOD_EXAMPLE)).catch(error);
  el('add-extra').onclick=()=>{const next=FoodPlanner.clone(plan);next.extras.push({name:el('extra-name').value.trim(),quantity:Number(el('extra-quantity').value),unit:el('extra-unit').value});commit(next).catch(error);};
  el('load-catalogue').onclick=()=>loadCatalogue().catch(failure=>{el('catalogue-status').textContent=failure.message;});
  el('category').onchange=chooseCategory;el('subcategory').onchange=()=>chooseSubcategory().catch(error);
  el('use-category').onclick=()=>selectNames().catch(error);
  el('clear-ai-selection').onclick=()=>{const next=FoodPlanner.clone(plan);next.aiSelection=[];commit(next).then(()=>{el('ai-sections').textContent='No product names selected for AI';}).catch(error);};
  el('load-recipes').onclick=()=>loadRecipes().catch(failure=>{el('recipe-status').textContent=failure.message;});
  ['recipe-search','recipe-diet','recipe-max-calories','recipe-unknown','recipe-saved'].forEach(id=>el(id).oninput=()=>{recipePage=0;showRecipes();});
  el('recipes-prev').onclick=()=>{recipePage--;showRecipes();};el('recipes-next').onclick=()=>{recipePage++;showRecipes();};
  el('import-recipe').onclick=()=>ask('import_source').catch(error);
  el('refresh-catalogue').onclick=()=>ask('refresh').catch(error);
  el('catalogue-prev').onclick=()=>loadPage(page-1).catch(error);el('catalogue-next').onclick=()=>loadPage(page+1).catch(error);el('product-search').oninput=showProducts;
  el('generate').onclick=()=>ask('generate').catch(error);el('replace').onclick=()=>ask('replace').catch(error);
  el('apply').onclick=()=>{try{const next=FoodPlanner.proposal(plan,pending,record.revision),suggestion=pending;pending=null;commit(next).then(()=>{render();}).catch(failure=>{pending=suggestion;error(failure);render();});}catch(failure){error(failure);}};
  el('discard').onclick=()=>{pending=null;render();};
  reload().then(()=>{
    if(!ready)return;
    try {const raw=(globalThis.agoraWorkflowInput||'').trim();if(raw.startsWith('{')){const result=JSON.parse(raw);if(result.schema==='agora.food-refresh/v1'||result.schema==='agora.recipe-import/v1'||result.schema==='agora.off-refresh/v1'){el('ai-status').textContent=result.message||'Reload the catalogue to check the refreshed snapshot.';return;}if(result.schema==='agora.food-proposal/v1'){FoodPlanner.proposal(plan,result,record.revision);pending=result;render();}}}
    catch(failure){error(failure);}
  });
}
