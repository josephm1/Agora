/* Pure deterministic operations. No network, model calls, storage or DOM access. */
var FoodPlanner = (() => {
  const SCHEMA = 'agora.food-plan/v1';
  const clone = value => JSON.parse(JSON.stringify(value));
  const positive = value => typeof value === 'number' && Number.isFinite(value) && value > 0 && value <= 1000000;
  const text = (value, max=160) => typeof value === 'string' && value.trim().length > 0 && value.length <= max;
  const units = {g:['mass',1,'g'],kg:['mass',1000,'g'],ml:['volume',1,'ml'],l:['volume',1000,'ml'],piece:['piece',1,'piece'],pack:['pack',1,'pack'],cup:['cup',1,'cup'],unspecified:['unspecified',1,'unspecified'],tsp:['tsp',1,'tsp'],tbsp:['tbsp',1,'tbsp']};
  const defaults=()=>({days:3,servings:2,mealPattern:'dinner',diet:'mixed',vegetarianDays:0,budget:null,calories:null,equipment:'ninja-11-in-1'});
  function preferences(plan){return {...defaults(),...(plan.preferences||{})};}
  function schedule(plan){
    const p=preferences(plan),slots=[];
    for(let day=1;day<=p.days;day++)for(const meal of p.mealPattern==='three-meals'?['breakfast','lunch','dinner']:['dinner'])
      slots.push({id:'day-'+day+'-'+meal,label:'Day '+day+' '+meal,servings:p.servings,day,diet:p.diet==='mixed'&&day<=p.vegetarianDays?'vegetarian':p.diet});
    return slots;
  }
  function nextBatch(plan){const missing=schedule(plan).filter(s=>!plan.menu.some(item=>item.id===s.id));return missing.filter(s=>s.day===missing[0]?.day);}
  function ingredient(item) {
    if (!item || !text(item.name) || !(item.quantity===null||positive(item.quantity)) || !units[item.unit] ||
      (item.productId != null && !text(item.productId))) throw Error('Invalid ingredient or quantity');
  }
  function validate(plan) {
    if (!plan || plan.schema !== SCHEMA || !Array.isArray(plan.recipes) || plan.recipes.length > 80 ||
      !Array.isArray(plan.menu) || plan.menu.length > 31 || !Array.isArray(plan.products) || plan.products.length > 120 ||
      !Array.isArray(plan.extras) || plan.extras.length > 80 || !plan.checked || typeof plan.checked !== 'object' || Array.isArray(plan.checked)) throw Error('Invalid food plan');
    const p=preferences(plan);
    if(!Number.isInteger(p.days)||p.days<1||p.days>7||!Number.isInteger(p.servings)||p.servings<1||p.servings>24||
      !['ninja-11-in-1','any'].includes(p.equipment)||!['dinner','three-meals'].includes(p.mealPattern)||!['mixed','vegetarian','vegan'].includes(p.diet)||
      !Number.isInteger(p.vegetarianDays)||p.vegetarianDays<0||p.vegetarianDays>p.days||
      !(p.budget===null||positive(p.budget))||!(p.calories===null||Number.isInteger(p.calories)&&p.calories>=100&&p.calories<=6000))throw Error('Invalid planning preferences');
    if(plan.aiSelection!==undefined&&(!Array.isArray(plan.aiSelection)||plan.aiSelection.length>120||plan.aiSelection.some(id=>typeof id!=='string')))throw Error('Invalid AI product selection');
    if(plan.savedRecipeIds!==undefined&&(!Array.isArray(plan.savedRecipeIds)||plan.savedRecipeIds.length>1000||new Set(plan.savedRecipeIds).size!==plan.savedRecipeIds.length||plan.savedRecipeIds.some(id=>!text(id,80))))throw Error('Invalid saved recipe selection');
    if(plan.savedRecipeTitles!==undefined&&(!Array.isArray(plan.savedRecipeTitles)||plan.savedRecipeTitles.length>1000||new Set(plan.savedRecipeTitles).size!==plan.savedRecipeTitles.length||plan.savedRecipeTitles.some(title=>!text(title))))throw Error('Invalid saved recipe titles');
    const ids = new Set();
    for (const recipe of plan.recipes) {
      if (!recipe || !text(recipe.id,80) || ids.has(recipe.id) || !text(recipe.title) || !positive(recipe.servings) || recipe.servings > 24 ||
        !Array.isArray(recipe.ingredients) || !recipe.ingredients.length || recipe.ingredients.length > 30 ||
        !Array.isArray(recipe.steps) || !recipe.steps.length || recipe.steps.length > 60 || recipe.steps.some(s=>!text(s,2000))) throw Error('Invalid recipe');
      ids.add(recipe.id); recipe.ingredients.forEach(ingredient);
    }
    const slots = new Set();
    for (const slot of plan.menu) {
      if (!slot || !text(slot.id,80) || slots.has(slot.id) || !text(slot.label,80) || !ids.has(slot.recipeId) || !positive(slot.servings) || slot.servings>24) throw Error('Invalid menu slot');
      slots.add(slot.id);
    }
    const products = new Set();
    for (const p of plan.products) {
      if (!p || !text(p.id) || products.has(p.id) || !text(p.title) || !(p.price == null || Number.isFinite(p.price) && p.price >= 0)) throw Error('Invalid product');
      if (p.pack && (!positive(p.pack.quantity) || !['g','kg','ml','l'].includes(p.pack.unit))) throw Error('Invalid pack size');
      products.add(p.id);
    }
    for (const item of [...plan.extras,...plan.recipes.flatMap(r=>r.ingredients)]) {
      ingredient(item); if (item.productId && !products.has(item.productId)) throw Error('Unknown product mapping');
    }
    if (Object.keys(plan.checked).length > 400 || Object.values(plan.checked).some(v=>v!==true)) throw Error('Invalid checklist');
    if (new TextEncoder().encode(JSON.stringify(plan)).length>62000) throw Error('Plan is too large; remove unused recipes');
    return plan;
  }
  function shopping(plan) {
    validate(plan);
    const groups = new Map(), products = new Map(plan.products.map(p=>[p.id,p]));
    function add(item, multiplier) {
      let [dimension,factor,unit] = units[item.unit]; let quantity = item.quantity===null?null:item.quantity * multiplier * factor;
      const p = products.get(item.productId);
      if (dimension==='pack' && p?.pack) { const spec=units[p.pack.unit]; dimension=spec[0];unit=spec[2];quantity=quantity===null?null:quantity*p.pack.quantity*spec[1]; }
      const identity = item.productId || item.name.trim().toLowerCase();
      const key=JSON.stringify([identity,dimension]);
      const row=groups.get(key)||{name:item.name,quantity:0,unit,dimension,productId:item.productId||null};
      row.quantity=row.quantity===null||quantity===null?null:row.quantity+quantity;groups.set(key,row);
    }
    for (const slot of plan.menu) {
      const recipe=plan.recipes.find(r=>r.id===slot.recipeId);
      recipe.ingredients.forEach(item=>add(item,slot.servings/recipe.servings));
    }
    plan.extras.forEach(item=>add(item,1));
    return [...groups.values()].map(row=>{
      row.quantity=row.quantity===null?null:Math.round(row.quantity*1000)/1000;
      row.key=JSON.stringify([row.productId||row.name.trim().toLowerCase(),row.dimension,row.quantity]);
      row.checked=plan.checked[row.key]===true;
      const p=products.get(row.productId);row.packs=null;row.cost=null;
      if(row.quantity!==null && p?.pack && units[p.pack.unit][0]===row.dimension) row.packs=Math.ceil(row.quantity/(p.pack.quantity*units[p.pack.unit][1])-1e-10);
      else if(row.quantity!==null && p && row.dimension==='pack') row.packs=Math.ceil(row.quantity);
      if(row.packs!=null && p.price!=null && p.currency==='EUR')row.cost=Math.round(row.packs*p.price*100)/100;
      return row;
    });
  }
  function proposal(plan, change, revision) {
    if(!change || change.schema!=='agora.food-proposal/v1' || change.baseRevision!==revision || !text(change.requestId,80)) throw Error('Proposal is stale or invalid; request a fresh suggestion');
    const next=clone(plan);
    if(change.recipe||change.recipes||change.ingredients||change.steps)throw Error('AI-written recipes are not accepted; select imported source recipes');
    function sourceRecipe(id){
      const value=next.recipes.find(r=>r.id===id);
      if(!value||value.source?.kind!=='imported'||!/^https:\/\//.test(value.source.url))throw Error('Choose a recipe imported from an actual source');
      return value;
    }
    function mappedRecipe(id,mappings,slotId){
      const source=sourceRecipe(id);
      if(!mappings||!mappings.length)return source.id;
      if(!Array.isArray(mappings)||mappings.length>30)throw Error('Invalid product-name mappings');
      const mapped=clone(source),seen=new Set();
      for(const mapping of mappings){
        if(mapping.quantity!==undefined||mapping.unit!==undefined||mapping.productId!==undefined)throw Error('Only name-based product mappings are accepted');
        if(seen.has(mapping.ingredientName))throw Error('Duplicate ingredient mapping');seen.add(mapping.ingredientName);
        const ingredients=mapped.ingredients.filter(i=>i.name===mapping.ingredientName),products=next.products.filter(p=>p.title===mapping.productName);
        if(ingredients.length!==1||products.length!==1)throw Error('Choose exact unique source ingredient and product names');
        ingredients[0].productId=products[0].id;
      }
      mapped.id=source.id+'-'+slotId+'-'+revision;
      if(next.recipes.some(r=>r.id===mapped.id))throw Error('Recipe revision already exists');
      mapped.adaptationNotes=['Selected catalogue products mapped by name. Source quantities and instructions preserved.'];
      next.recipes.push(mapped);return mapped.id;
    }
    if(change.action==='options') {
      if(!Array.isArray(change.options)||!change.options.length||change.options.length>3)throw Error('Invalid change options');
      change.options.forEach(option=>{if(!['replace','replace_product','omit_optional'].includes(option.action))throw Error('Invalid option action');proposal(plan,{...option,schema:change.schema,requestId:change.requestId,baseRevision:change.baseRevision,slotId:change.slotId},revision);});
      return clone(plan);
    }
    if(change.action==='replace_product') {
      const slot=next.menu.find(s=>s.id===change.slotId);if(!slot)throw Error('Meal no longer exists');
      const original=next.recipes.find(r=>r.id===slot.recipeId), replacement=clone(original);sourceRecipe(original.id);
      const matches=next.products.filter(p=>p.title===change.productName);
      if(matches.length!==1)throw Error('Choose one exact product from the local selection');
      const ingredients=replacement.ingredients.filter(i=>i.name===change.ingredientName);
      if(ingredients.length!==1)throw Error('Choose one exact recipe ingredient');
      ingredients[0].productId=matches[0].id;replacement.adaptationNotes=['Product swap for '+change.ingredientName+': '+change.productName+'. Source quantities and instructions preserved.'];
      replacement.id=original.id+'-product-'+revision;
      if(next.recipes.some(r=>r.id===replacement.id))throw Error('Recipe revision already exists');
      next.recipes.push(replacement);slot.recipeId=replacement.id;
    } else if(change.action==='omit_optional') {
      const slot=next.menu.find(s=>s.id===change.slotId);if(!slot)throw Error('Meal no longer exists');
      const original=next.recipes.find(r=>r.id===slot.recipeId);sourceRecipe(original.id);
      const ingredient=original.ingredients.filter(i=>i.name===change.ingredientName);
      if(ingredient.length!==1||ingredient[0].optional!==true)throw Error('Only a source-labelled optional ingredient can be omitted');
      const modified=clone(original);modified.id=original.id+'-omit-'+revision;modified.ingredients=modified.ingredients.filter(i=>i.name!==change.ingredientName);
      modified.adaptationNotes=['Optional '+change.ingredientName+' omitted. Original source steps are preserved; they may still mention it.'];
      next.recipes.push(modified);slot.recipeId=modified.id;
    } else if(change.action==='replace') {
      const slot=next.menu.find(s=>s.id===change.slotId);if(!slot)throw Error('Meal no longer exists');
      slot.recipeId=mappedRecipe(change.sourceRecipeId,change.mappings,slot.id);
    } else if(change.action==='generate') {
      const batch=nextBatch(plan);
      if(!batch.length||!Array.isArray(change.selections)||change.selections.length!==batch.length||new Set(change.selections.map(s=>s.slotId)).size!==batch.length||change.selections.some(s=>!batch.some(b=>b.id===s.slotId)))throw Error('Select sourced recipes for the requested meal slots');
      for(const choice of change.selections){const slot=batch.find(s=>s.id===choice.slotId);const recipeId=mappedRecipe(choice.sourceRecipeId,choice.mappings,slot.id);next.menu.push({id:slot.id,label:slot.label,recipeId,servings:slot.servings});}
    } else throw Error('Unknown proposal action');
    validate(next);
    const keys=new Set(shopping(next).map(r=>r.key));next.checked=Object.fromEntries(Object.entries(next.checked).filter(([key])=>keys.has(key)));
    return next;
  }
  return {SCHEMA,clone,validate,shopping,proposal,preferences,schedule,nextBatch,empty:()=>({schema:SCHEMA,preferences:defaults(),recipes:[],menu:[],products:[],aiSelection:[],extras:[],checked:{}})};
})();
if(typeof module!=='undefined')module.exports=FoodPlanner;
