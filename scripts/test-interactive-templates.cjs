/* Folder-backed setup through the real sandbox: no AI/network while choosing inputs. */
module.exports = async ({page,open,assert}) => {
  const fs = require('node:fs'), path = require('node:path');
  const template = JSON.parse(fs.readFileSync(path.join(__dirname,'../examples/interactive-templates/osce/osce.agora-template.json'),'utf8'));
  assert.equal(template.linkedSkill, undefined);
  assert.equal(template.resourceFiles.length, 0);
  assert.equal(template.sourceDirectory, '/mnt/shared/bmj');
  const files = ['Cardiology/Heart failure.md','Cardiology/Acute coronary syndrome.md','Surgery/Appendicitis.md'];
  const answer = {...template.answer,jsExpressions:['globalThis.agoraSourceFiles = '+JSON.stringify(files),...template.answer.jsExpressions]};
  const viewport = page.viewportSize();
  await page.setViewportSize({width:360,height:900});
  let frame = await open(answer,false,false,{eInkMode:true,fontScale:1});
  await frame.getByRole('button',{name:'Cardiology',exact:true}).waitFor();
  assert.equal(await frame.locator('body').evaluate(el=>el.scrollWidth<=el.clientWidth),true);
  const sends = () => page.evaluate(()=>window.nativeEvents.filter(e=>e.method==='sendPrompt'));
  assert.equal((await sends()).length,0);
  await frame.getByRole('button',{name:'Cardiology',exact:true}).click();
  await frame.getByRole('button',{name:'Heart failure',exact:true}).click();
  await frame.locator('#count').focus(); await frame.locator('#count').press('ArrowRight');
  assert.equal((await sends()).length,0);
  assert.equal(await frame.locator('#count-value').textContent(),'13');
  const report = path.join(__dirname,'../app/build/reports/interactive-templates');fs.mkdirSync(report,{recursive:true});
  assert.equal(await frame.locator('.condition[aria-pressed=true]').evaluate(el => getComputedStyle(el).backgroundColor),'rgb(0, 0, 0)');
  await page.screenshot({path:path.join(report,'folder-osce-eink.png'),fullPage:true});
  await frame.locator('#start').click();
  await page.waitForFunction(()=>window.nativeEvents.some(e=>e.method==='sendPrompt'));
  assert.equal((await sends()).length,1);
  assert.deepEqual((await sends())[0].value.sourceFiles,['Cardiology/Heart failure.md']);
  assert.deepEqual(JSON.parse((await sends())[0].value.text),{specialty:'Cardiology',condition:'Heart failure',questions:13});
  assert.ok(!(await sends())[0].value.text.includes('examiner.md'));
  assert.ok(!(await sends())[0].value.text.includes('Appendicitis'));
  assert.equal(await frame.locator('#start').isDisabled(),true);
  await page.setViewportSize({width:900,height:1000});
  frame=await open(answer,false,false,{eInkMode:false,fontScale:1});
  await frame.getByRole('button',{name:'Surgery',exact:true}).click();
  await frame.getByRole('button',{name:'Appendicitis',exact:true}).click();
  assert.equal((await sends()).length,0);
  await frame.locator('main').evaluate(async () => {
    await Promise.all(document.getAnimations().filter(a => a.effect.getComputedTiming().iterations !== Infinity)
      .map(a => a.finished.catch(() => {})));
  });
  assert.equal(await frame.locator('.condition[aria-pressed=true]').evaluate(el => {
    const style = getComputedStyle(el); return style.color !== style.backgroundColor;
  }),true);
  await page.screenshot({path:path.join(report,'folder-osce-regular.png'),fullPage:true});
  const review=JSON.parse(fs.readFileSync(path.join(__dirname,'../examples/interactive-templates/osce/review.agora-template.json'),'utf8'));
  frame=await open({...review.answer,jsExpressions:['globalThis.agoraWorkflowInput="Review outcome"',...review.answer.jsExpressions]},false,false,{eInkMode:true,fontScale:1});
  assert.equal(await frame.locator('#outcome').textContent(),'Review outcome');
  await frame.locator('#focus').fill('Heart failure assessment');
  assert.equal((await sends()).length,0);
  await frame.locator('#continue').click();
  await page.waitForFunction(()=>window.nativeEvents.some(e=>e.method==='sendPrompt'));
  assert.deepEqual(JSON.parse((await sends())[0].value),{focus:'Heart failure assessment'});
  await page.setViewportSize(viewport);
};
