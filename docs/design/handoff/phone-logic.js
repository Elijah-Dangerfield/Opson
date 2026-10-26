data-dc-script data-props="{&quot;$preview&quot;:{&quot;width&quot;:410,&quot;height&quot;:864},&quot;screen&quot;:{&quot;editor&quot;:&quot;enum&quot;,&quot;options&quot;:[&quot;onb&quot;,&quot;today&quot;,&quot;log&quot;,&quot;dayclose&quot;,&quot;week&quot;,&quot;more&quot;,&quot;modes&quot;,&quot;reminders&quot;,&quot;weight&quot;,&quot;integrations&quot;,&quot;support&quot;,&quot;widgets&quot;,&quot;foods&quot;,&quot;nutrients&quot;,&quot;mealbuilder&quot;,&quot;customfood&quot;,&quot;targets&quot;,&quot;fasting&quot;,&quot;water&quot;],&quot;default&quot;:&quot;today&quot;,&quot;tsType&quot;:&quot;string&quot;},&quot;palette&quot;:{&quot;editor&quot;:&quot;enum&quot;,&quot;options&quot;:[&quot;pine&quot;,&quot;espresso&quot;,&quot;clay&quot;,&quot;plum&quot;],&quot;default&quot;:&quot;pine&quot;,&quot;tsType&quot;:&quot;string&quot;},&quot;theme&quot;:{&quot;editor&quot;:&quot;enum&quot;,&quot;options&quot;:[&quot;light&quot;,&quot;dark&quot;],&quot;default&quot;:&quot;light&quot;,&quot;tsType&quot;:&quot;string&quot;},&quot;textSize&quot;:{&quot;editor&quot;:&quot;enum&quot;,&quot;options&quot;:[&quot;normal&quot;,&quot;large&quot;],&quot;default&quot;:&quot;normal&quot;,&quot;tsType&quot;:&quot;string&quot;},&quot;bigDay&quot;:{&quot;editor&quot;:&quot;boolean&quot;,&quot;default&quot;:false,&quot;tsType&quot;:&quot;boolean&quot;},&quot;settings&quot;:{&quot;editor&quot;:null,&quot;tsType&quot;:&quot;object&quot;},&quot;onSettings&quot;:{&quot;editor&quot;:null,&quot;tsType&quot;:&quot;function&quot;}}">
// ---- VisibilityPolicy: the only place nutrition numbers are decided and formatted ----
const M = ['calories','remaining','energyOut','protein','carbs','fat','fiber','micros','weight','water'];
const METRIC_LABEL = {calories:'Calories',remaining:'Left in range',energyOut:'Energy expended',protein:'Protein',carbs:'Carbs',fat:'Fat',fiber:'Fiber',micros:'Vitamins, minerals',weight:'Weight',water:'Water'};
const fill = v => Object.fromEntries(M.map(k => [k, v]));
const FULL_VIS = {...fill('show'), micros:'hide'};
const PRESETS = {
  full:     {label:'Full numbers',   for:'Everything visible. Watch sync shapes your target.', today:'full', vis:FULL_VIS},
  delayed:  {label:'Delayed reveal', for:'Log all day. Totals open at a time you pick.', today:'full', revealOn:'day', vis:{...fill('delay'), micros:'hide', weight:'soften'}},
  blind:    {label:'Blind',          for:'No calories anywhere in the app.', today:'meals', vis:fill('hide')},
  nutrient: {label:'Nutrient focus', for:'Calories hidden. Protein and fiber as fill bars.', today:'full', vis:{...fill('hide'), protein:'soften', fiber:'soften', weight:'soften'}},
  rhythm:   {label:'Rhythm',         for:'A meal checklist with reminders. Amounts optional.', today:'rhythm', mealCount:true, vis:fill('hide')},
  explorer: {label:'Explorer',       for:'Safe foods, new foods, small wins for trying.', today:'explorer', vis:fill('hide')},
  mindful:  {label:'Mindful',        for:'Hunger, fullness and how it felt. No nutrition data.', today:'meals', banner:'Tag how meals felt when you add them. No nutrition data in this mode.', vis:fill('hide')},
  fuel:     {label:'Fuel',           for:'Eating enough first. Protein and meal count up front.', today:'full', mealCount:true, vis:{...fill('hide'), calories:'soften', protein:'show', fiber:'show', weight:'soften'}},
  sprint:   {label:'Sprint',         for:'Track for two weeks to learn, then take a break.', today:'full', banner:'Sprint, day 9 of 14. Tracking takes a break on Nov 1.', vis:FULL_VIS},
  coast:    {label:'Coast',          for:'Light logging and one weekly check.', today:'meals', revealOn:'week', banner:'Your weekly check is on Sunday evening.', vis:{...fill('hide'), calories:'delay', weight:'soften'}},
};
Object.keys(PRESETS).forEach(k => { if (PRESETS[k].vis !== FULL_VIS) PRESETS[k].vis = {...PRESETS[k].vis, water:'soften'}; });
const FAST_OK = ['full','sprint'];
const FAST_PLANS = {'12:12':{open:'7:00 am', close:'7:00 pm'}, '14:10':{open:'9:00 am', close:'7:00 pm'}, '16:8':{open:'11:00 am', close:'7:00 pm'}};
const ORDER = ['full','delayed','blind','nutrient','rhythm','explorer','mindful','fuel','sprint','coast'];
const LEVEL = {show:3, soften:2, delay:1, hide:0};
const GOAL = {target:2100, min:1900, max:2300, protein:120, carbs:250, fat:70, fiber:30};
// TODO(dietitian): GOAL_FLOOR_KCAL must be set by an ED-informed dietitian before launch. Do not invent a value.
// While null, setGoal() refuses any target below the current one, so nothing can be lowered unreviewed.
const GOAL_FLOOR_KCAL = null;
const WATCH = {resting:1580, movement:260, workout:150};
const DEFAULTS = {waterGoal:8, glassMl:250, waterUnit:'ml', waterRem:false, ringMode:'target', trRange:'1W', trCards:{intake:true, recap:true, nutrients:true, expended:true, weight:true}, fasting:false, fastToday:true, fastPlan:'14:10', mode:'full', overrides:{}, lock:false, exercise:'some', quiet:true, tone:'plain', weekStart:'Mon', askTags:true,
  events:{bigday:'full', newfood:'subtle', recap:'subtle', reminders:'subtle'},
  mealRem:{Breakfast:true, Lunch:true, Snack:false, Dinner:true}, changedAt:null};

const FOODS = [
  {id:'rice', name:'Jasmine rice, cooked', tag:'', per:{kcal:130,p:2.7,c:28,f:.3,fib:.4,ca:1}, units:[{k:'cup',g:158},{k:'g',g:1}]},
  {id:'sinangag', name:'Garlic fried rice (sinangag)', tag:'Filipino', per:{kcal:180,p:3.2,c:30,f:5,fib:.6,ca:1}, units:[{k:'cup',g:160},{k:'g',g:1}]},
  {id:'arroz', name:'Arroz con pollo', tag:'Latin American', per:{kcal:150,p:9,c:17,f:5,fib:1,ca:2}, units:[{k:'plate',g:350},{k:'g',g:1}]},
  {id:'kabsa', name:'Chicken kabsa', tag:'Saudi', per:{kcal:165,p:10,c:20,f:5,fib:1,ca:2}, units:[{k:'plate',g:380},{k:'g',g:1}]},
  {id:'ricepud', name:'Rice pudding', tag:'', per:{kcal:118,p:3.6,c:20,f:2.5,fib:.2,ca:10}, units:[{k:'package',g:113},{k:'cup',g:240},{k:'g',g:1}]},
  {id:'adobo', name:'Chicken adobo', tag:'Filipino', per:{kcal:200,p:17,c:3,f:13,fib:0,ca:1}, units:[{k:'thigh with sauce',g:155},{k:'g',g:1}]},
  {id:'yogurt', name:'Greek yogurt, plain', tag:'', per:{kcal:59,p:10,c:3.6,f:.4,fib:0,ca:11}, units:[{k:'package',g:170},{k:'cup',g:245},{k:'g',g:1}]},
  {id:'mango', name:'Mango sticky rice', tag:'Thai', per:{kcal:200,p:2.5,c:38,f:5,fib:1,ca:1}, units:[{k:'plate',g:220},{k:'g',g:1}]},
  {id:'poutine', name:'Poutine', tag:'Canadian', per:{kcal:230,p:6,c:24,f:12,fib:2,ca:8}, units:[{k:'small box',g:300},{k:'g',g:1}]},
  {id:'oats', name:'Oat porridge with milk', tag:'', per:{kcal:120,p:4,c:20,f:2.5,fib:3,ca:9}, units:[{k:'bowl',g:250},{k:'g',g:1}]},
  {id:'banana', name:'Banana', tag:'', per:{kcal:89,p:1.1,c:23,f:.3,fib:2.6,ca:0}, units:[{k:'medium',g:118},{k:'g',g:1}]},
];
const KNOWN = new Set(['rice','adobo','yogurt','oats','banana','sinangag']);
const it = (name, amount, precision, kcal, p=0, c=0, f=0, fib=0) => ({name, amount, precision, kcal, p, c, f, fib});
const MEALS = () => [
  {name:'Breakfast', time:'8:10 am', planned:'around 8:00', items:[it('Oat porridge with milk','1 bowl','Weighed',300,10,50,6,8), it('Banana','1 medium','Estimate',105,1,27,0,3), it('Coffee with milk','1 mug','Estimate',40,2,3,2,0)]},
  {name:'Lunch', time:'12:40 pm', planned:'around 12:30', items:[it('Chicken adobo','1 thigh with sauce','Estimate',310,26,5,20,0), it('Jasmine rice, cooked','1 cup','Estimate',205,4,45,0,1)]},
  {name:'Snack', time:'3:30 pm', planned:'around 3:30', items:[it('Apple','1 medium','Estimate',95,0,25,0,4), it('Peanut butter','2 tbsp','Weighed',190,8,7,16,2)]},
  {name:'Dinner', time:'', planned:'around 7:00', items:[]},
];
const BIG_DINNER = [it('Pancit canton','2 plates','Estimate',760,28,96,26,5), it('Lechon kawali','3 pieces','Estimate',640,30,2,56,0), it('Birthday cake','1 slice','Named',450,5,58,22,1)];
const YESTERDAY = {Breakfast:[it('Greek yogurt, plain','1 package','Weighed',100,17,6,1,0), it('Banana','1 medium','Estimate',105,1,27,0,3)], Lunch:[it('Chicken kabsa','1 plate','Estimate',630,38,76,19,4)], Snack:[it('Rice pudding','1 package','Weighed',135,4,23,3,0)], Dinner:[it('Arroz con pollo','1 plate','Estimate',525,32,60,18,4), it('Side salad','1 bowl','Named',60,2,8,3,3)]};
const SAVED = [
  {name:'Usual breakfast', items:[it('Oat porridge with milk','1 bowl','Weighed',300,10,50,6,8), it('Banana','1 medium','Estimate',105,1,27,0,3), it('Coffee with milk','1 mug','Estimate',40,2,3,2,0)]},
  {name:'Work lunch', items:[it('Chicken adobo','1 thigh with sauce','Estimate',310,26,5,20,0), it('Jasmine rice, cooked','1 cup','Estimate',205,4,45,0,1)]},
  {name:'Yogurt and fruit', items:[it('Greek yogurt, plain','1 package','Weighed',100,17,6,1,0), it('Berries','1 cup','Estimate',70,1,17,0,4)]},
];
const PHOTO = [it('Chicken breast, grilled','about 120 g','Estimate',198,37,0,4,0), it('Olive oil','1 tbsp','Estimate',119,0,0,14,0), it('Broccoli, steamed','2 cups','Estimate',62,5,12,1,5)];
const QUICK = [it('Egg, large','2','Estimate',156,12,1,10,0), it('Toast, white','1 slice','Estimate',80,3,15,1,1)];
// Oct 17 to 25. null = untracked day.
const DAYS = [
  {d:'Sat',n:17,kcal:2300,meals:3},{d:'Sun',n:18,kcal:null,meals:0},{d:'Mon',n:19,kcal:2050,meals:4},{d:'Tue',n:20,kcal:1980,meals:3,nf:'Chicken kabsa'},
  {d:'Wed',n:21,kcal:2240,meals:3},{d:'Thu',n:22,kcal:1890,meals:4,nf:'Rice pudding'},{d:'Fri',n:23,kcal:2410,meals:4},{d:'Sat',n:24,kcal:2160,meals:3,nf:'Poutine'},{d:'Sun',n:25,kcal:null,meals:null,today:true}];
const BREAKFAST = {17:0,18:0,19:1,20:1,21:0,22:1,23:1,24:0,25:1};
const WEIGHTS = Array.from({length:90}, (_, i) => +(74.6 - i*0.014 + Math.sin(i*1.7)*0.35 + Math.cos(i*0.9)*0.2).toFixed(1));
const OFF = {on:false, soft:false, text:''};
const MEAL_STYLE = {Breakfast:{icon:'bakery_dining', c:'carbs'}, Lunch:{icon:'lunch_dining', c:'fat'}, Snack:{icon:'nutrition', c:'fiber'}, Dinner:{icon:'dinner_dining', c:'protein'}};
const mc = n => { const s = MEAL_STYLE[n] || MEAL_STYLE.Snack; return {icon:s.icon, color:`var(--c-${s.c})`, tint:`var(--t-${s.c})`}; };
// Mock percent of daily target: [today, week average]
const MICROS = [
  {title:'Vitamins', color:'var(--c-fiber)', rows:[['Vitamin A','700 µg',62,78],['Vitamin B12','2.4 µg',88,104],['Vitamin C','90 mg',134,96],['Vitamin D','15 µg',22,31],['Vitamin E','15 mg',41,48],['Vitamin K','120 µg',73,112],['Folate','400 µg',57,69]]},
  {title:'Minerals', color:'var(--c-fat)', rows:[['Calcium','1,000 mg',46,58],['Iron','18 mg',64,71],['Magnesium','420 mg',52,60],['Potassium','4,700 mg',49,55],['Sodium','2,300 mg limit',81,92],['Zinc','11 mg',70,77]]},
];
const RECENT_IDS = ['banana','yogurt','adobo','rice','mango','poutine'];
const FOOD_META = {rice:['rice_bowl','carbs'], sinangag:['rice_bowl','carbs'], arroz:['dinner_dining','protein'], kabsa:['set_meal','protein'], ricepud:['icecream','fiber'], adobo:['kebab_dining','protein'], yogurt:['icecream','fat'], mango:['nutrition','carbs'], poutine:['fastfood','carbs'], oats:['breakfast_dining','carbs'], banana:['nutrition','carbs']};
const SHORT = {rice:'Rice', yogurt:'Yogurt', adobo:'Adobo', oats:'Oats', banana:'Banana'};
const FAVS = ['banana','yogurt','rice','adobo','oats'];
const PICKS = {Breakfast:['oats','yogurt','banana'], Lunch:['adobo','rice','kabsa'], Snack:['banana','yogurt','ricepud'], Dinner:['arroz','sinangag','adobo']};
const WEEKLY = [2150,2080,2210,1990,2120,2240,2060,2180,2010,2140,2200,2100];
const OUT7 = [2010,2240,1950,2380,2105,2190,2330];
const MB0 = {name:'', slot:'Lunch', items:[], q:''};
const CF0 = {name:'', brand:'', amt:'1', unit:'serving', kcal:'', p:'', c:'', f:'', fib:''};
const WEEKDAYS = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
const TODAY = new Date(2026, 9, 25);
const dayInfo = n => { const d = new Date(TODAY); d.setDate(d.getDate() + n); const date = d.toLocaleDateString('en-US', {month:'short', day:'numeric'});
  return {label:n === 0 ? 'Today' : n === -1 ? 'Yesterday' : n === 1 ? 'Tomorrow' : d.toLocaleDateString('en-US', {weekday:'short', month:'short', day:'numeric'}), date:(Math.abs(n) <= 1 ? WEEKDAYS[d.getDay()] + ', ' : '') + date}; };
const mealsFor = n => {
  if (n > 0) return MEALS().map(m => ({...m, time:'', items:[]}));
  const src = [YESTERDAY, {Breakfast:SAVED[0].items, Lunch:SAVED[1].items, Snack:SAVED[2].items, Dinner:YESTERDAY.Lunch}, {Breakfast:YESTERDAY.Breakfast, Lunch:SAVED[1].items, Snack:YESTERDAY.Snack, Dinner:SAVED[1].items}][(-n - 1) % 3];
  const times = {Breakfast:'8:20 am', Lunch:'12:50 pm', Snack:'3:45 pm', Dinner:'7:10 pm'};
  return MEALS().map(m => ({...m, time:times[m.name], items:(src[m.name] || []).map(x => ({...x}))}));
};

class Component extends DCLogic {
  scrollRef = React.createRef();
  swRef = React.createRef();
  componentDidMount() { const pg = this.props.swPage; if (pg) setTimeout(() => { const el = this.swRef.current; if (el) el.scrollLeft = pg * el.clientWidth; this.setState({swIdx:pg}); }, 60); }
  state = this.initState();

  initState() {
    const p = this.props || {};
    const meals = MEALS();
    if (p.bigDay) meals[3] = {...meals[3], time:'7:30 pm', items:BIG_DINNER};
    return {
      screen:p.screen || 'today', logTab:p.logTab || 'search', slot:'Dinner', sheet:p.sheet || null, foodId:'rice',
      unitIdx:0, qty:1, precision:'Estimate', hunger:null, fullness:null, query:'rice',
      photoNote:'Palm of chicken, a drizzle of oil, big pile of broccoli, no butter, lots of salt',
      photoEst:!!p.photoEstimated, photoItems:PHOTO.map(x => ({...x})), quickText:'2 eggs and toast', quickParsed:!!p.quickParsed, quickNum:'',
      nutRange:p.nutRange || 'day', prevScreen:'today', meals, day:0, dayStore:{}, closed:{}, ate:{}, revealed:!!p.revealed, committed:false, dayNote:'', weightInput:'', weightSaved:false,
      friction:p.friction ? {patch:{mode:'full', overrides:{}}, what:'Every number'} : null, frictionLeft:p.friction ? 7 : 0, reason:null,
      toast:null, onbStep:p.onbStep || 0, onb:{why:[], feel:null, notice:[], hard:false}, delConfirm:false,
      basket:p.demo ? [it('Banana','1 medium','Estimate',105,1.3,27,.4,3.1), it('Greek yogurt, plain','1 package','Estimate',100,17,6,.7,0)] : [],
      loose:[it('Sparkling water','1 can','Named',0), it('Granola bar','1 bar','Estimate',190,4,29,7,2)], looseStore:{},
      swIdx:p.swPage || 0, calMonth:0, water:{0:750}, menuMeal:'Dinner', noteVoice:false, noteDraft:'', recording:false, recDone:false, feelDraft:null, waterCustom:'',
      mealNotes:{Lunch:{voice:false, text:'Ate outside with Sam. Felt relaxed.'}}, mealFeel:{Breakfast:{hunger:'Hungry', full:'Satisfied', mood:['Calm']}}, clearAsk:false, fastActive:false,
      lq:'', afReturn:false, wRange:'1M', goalAdj:0, macroMode:'ratio', savedMeals:SAVED.map(x => ({...x})), customFoods:[],
      mb:p.demo ? {name:'Sunday breakfast', slot:'Breakfast', items:[it('Oat porridge with milk','1 bowl','Estimate',300,10,50,6,8), it('Banana','1 medium','Estimate',105,1,27,0,3)], q:''} : {...MB0},
      cf:{...CF0},
      local:{...DEFAULTS, ...(p.settings || {})},
    };
  }

  componentDidUpdate(prev) {
    const p = this.props;
    if ((prev.screen !== p.screen || prev.screenNonce !== p.screenNonce) && p.screen) { if (p.screen === 'onb') this.setState({onbStep:0, onbPick:null}); this.go(p.screen); }
    if (prev.bigDay !== p.bigDay) {
      const meals = MEALS();
      if (p.bigDay) meals[3] = {...meals[3], time:'7:30 pm', items:BIG_DINNER};
      this.setState({meals, day:0, dayStore:{}, committed:false});
    }
  }
  componentWillUnmount() { clearInterval(this.timer); clearTimeout(this.toastT); }

  S() {
    const base = this.props.onSettings ? (this.props.settings || {}) : this.state.local;
    return {...DEFAULTS, ...base, overrides:{...(base.overrides || {})}, events:{...DEFAULTS.events, ...(base.events || {})}, mealRem:{...DEFAULTS.mealRem, ...(base.mealRem || {})}, trCards:{...DEFAULTS.trCards, ...(base.trCards || {})}};
  }
  update(patch) {
    if (this.props.onSettings) this.props.onSettings(patch);
    else this.setState(s => ({local:{...s.local, ...patch}}));
  }

  // Policy
  vis(m, S = this.S()) { return S.overrides[m] ?? (PRESETS[S.mode] || PRESETS.full).vis[m]; }
  revealed(surface, S) {
    const P = PRESETS[S.mode] || PRESETS.full;
    if (P.revealOn === 'week') return surface === 'week';
    if (this.state.day < 0) return true;
    return surface === 'week' || surface === 'dayclose' || this.state.revealed;
  }
  nv(m, value, o = {}) {
    const S = this.S();
    let v = this.vis(m, S);
    if (v === 'delay') v = this.revealed(o.surface, S) ? 'show' : 'hide';
    if (v === 'hide' || value == null) return OFF;
    if (v === 'soften') { const t = this.soften(m, value, o); return t ? {on:true, soft:true, text:t} : OFF; }
    return {on:true, soft:false, text:this.format(m, value, o)};
  }
  format(m, v, o) {
    if (m === 'water') { const oz = o.unit === 'oz', f = x => oz ? Math.round(x / 29.57) : Math.round(x).toLocaleString('en-US'); return `${f(v)} of ${f(o.of || 2000)} ${oz ? 'fl oz' : 'ml'}`; }
    const n = Math.round(v).toLocaleString('en-US');
    if (m === 'weight') return v.toFixed(1) + ' kg';
    if (m === 'micros') return n + '%';
    if (['protein','carbs','fat','fiber'].includes(m)) return n + ' g' + (o.of ? ' of ' + o.of + ' g' : '');
    if (m === 'remaining') {
      if (o.eaten >= o.min && o.eaten <= o.max) return 'On target';
      if (o.eaten > o.max) return 'Above your range today';
      return n + ' left';
    }
    if (m === 'energyOut') return o.bare ? n : n + ' kcal';
    return o.bare ? n : n + ' kcal';
  }
  soften(m, v, o) {
    if (m === 'water') return null;
    if (m === 'weight') return 'Trend only';
    if (!o.of) return null;
    const p = v / o.of;
    if (m === 'calories') return p < .35 ? 'Early in the day' : p < .7 ? 'Partway through' : p < .92 ? 'Most of the way' : p <= 1.1 ? 'In your range' : 'A bigger day';
    if (m === 'micros') return p < .5 ? 'Some' : p < .9 ? 'Most' : 'Plenty';
    return p < .4 ? 'Building' : p < .85 ? 'Getting there' : 'Plenty today';
  }
  score(S) { return M.reduce((a, m) => a + LEVEL[this.vis(m, S)], 0); }

  requestChange(patch, what) {
    const S = this.S(), next = {...S, ...patch};
    if (S.lock && this.score(next) > this.score(S)) {
      clearInterval(this.timer);
      this.setState({friction:{patch, what}, frictionLeft:10, reason:null, sheet:'friction'});
      this.timer = setInterval(() => this.setState(s => { if (s.frictionLeft <= 1) clearInterval(this.timer); return {frictionLeft:Math.max(0, s.frictionLeft - 1)}; }), 1000);
      return;
    }
    this.update(patch);
    this.flash(this.score(next) < this.score(S) ? 'Saved. Fewer numbers from now on.' : 'Saved.');
  }
  setGoal(kcal) {
    // Floor mechanism. Real floor comes from GOAL_FLOOR_KCAL once a dietitian sets it.
    const floor = GOAL_FLOOR_KCAL ?? GOAL.target;
    return Math.max(kcal, floor);
  }

  addWater(ml) { const g = ml || this.S().glassMl; this.setState(s => ({water:{...s.water, [s.day]:Math.max(0, (s.water[s.day] ?? (s.day < 0 ? 1500 : 0)) + g)}})); }
  flash(text) { clearTimeout(this.toastT); this.setState({toast:text}); this.toastT = setTimeout(() => this.setState({toast:null}), 2600); }
  go(screen) { this.setState(s => ({screen, sheet:null, prevScreen:screen === 'nutrients' && s.screen !== 'nutrients' ? s.screen : s.prevScreen})); if (this.scrollRef.current) this.scrollRef.current.scrollTop = 0; }
  openLog(slot) { this.setState({slot}); this.go('log'); }
  setDay(n) {
    this.setState(s => { const store = {...s.dayStore, [s.day]:s.meals}; const ls = {...(s.looseStore || {}), [s.day]:s.loose}; return {looseStore:ls, loose:ls[n] || [], dayStore:store, day:n, meals:store[n] || mealsFor(n), ate:{}, committed:false}; });
    if (this.scrollRef.current) this.scrollRef.current.scrollTop = 0;
  }
  openQuick(slot) { this.setState(s => ({slot:slot || s.slot, sheet:'quick', lq:''})); }
  logVia(tab) { this.setState({logTab:tab}); this.go('log'); }
  addItems(items, slot = this.state.slot, isNew) {
    const S = this.S();
    if (S.mealGroups === false) slot = 'Uncategorized';
    if (slot === 'Uncategorized') { this.setState(s => ({loose:[...s.loose, ...items], sheet:null, fastActive:false})); this.go('today'); this.flash('Added.'); return; }
    this.setState(s => ({meals:s.meals.map(m => m.name === slot ? {...m, time:m.time || '6:50 pm', items:[...m.items, ...items]} : m), sheet:null, fastActive:false}));
    this.go('today');
    if (isNew && S.events.newfood !== 'off') this.flash(S.events.newfood === 'full' ? `First time logging ${items[0].name}. Nice.` : `New food logged: ${items[0].name}`);
    else this.flash(`Added to ${slot.toLowerCase()}.`);
  }

  seg(opts, cur, pick) { return opts.map(o => { const k = o.k ?? o, a = k === cur; return {label:o.label ?? o, onClick:() => pick(k), bg:a ? 'var(--surface)' : 'transparent', fg:a ? 'var(--ink)' : 'var(--muted)'}; }); }
  chips(opts, isOn, pick) { return opts.map(o => { const a = isOn(o); return {label:o, onClick:() => pick(o), bd:a ? 'var(--accent)' : 'var(--line)', bg:a ? 'var(--accent)' : 'var(--surface)', fg:a ? 'var(--on-accent)' : 'var(--ink)'}; }); }
  toggle(on, fn) { return {on, onClick:fn, bg:on ? 'var(--accent)' : 'var(--track)', jc:on ? 'flex-end' : 'flex-start'}; }
  radio(a) { return {ring:a ? 'var(--accent)' : 'var(--line)', dot:a ? 'var(--accent)' : 'transparent'}; }

  renderVals() {
    const st = this.state, S = this.S(), P = PRESETS[S.mode] || PRESETS.full, warm = S.tone === 'warm';
    const nv = (m, v, o) => this.nv(m, v, o);
    const all = [...st.meals.flatMap(m => m.items), ...st.loose];
    const groupsOn = S.mealGroups !== false;
    const sum = k => all.reduce((a, i) => a + (i[k] || 0), 0);
    const eaten = sum('kcal');
    const act = WATCH.movement + WATCH.workout;
    const adj = S.exercise === 'all' ? act : S.exercise === 'some' ? Math.round(act / 2) : 0;
    const baseT = GOAL.target + st.goalAdj, target = baseT + adj, min = GOAL.min + st.goalAdj + adj, max = GOAL.max + st.goalAdj + adj, scaleMax = max * 1.2;
    const pct = x => Math.max(0, Math.min(100, x * 100)).toFixed(1) + '%';
    const go = Object.fromEntries(['foods','nutrients','today','log','dayclose','week','more','modes','reminders','weight','integrations','support','widgets','mealbuilder','customfood','targets','fasting','water'].map(k => [k, () => this.go(k)]));
    const is = Object.fromEntries(['foods','nutrients','onb','today','log','dayclose','week','more','modes','reminders','weight','integrations','support','widgets','mealbuilder','customfood','targets','fasting','water'].map(k => [k, st.screen === k]));
    const slotLower = st.slot.toLowerCase();
    const doneMeals = st.meals.filter(m => m.items.length || st.ate[m.name]).length;
    const allFoods = [...st.customFoods, ...FOODS];

    // Today
    const tEaten = nv('calories', eaten, {surface:'today', of:target, bare:true});
    const tEnergy = nv('energyOut', WATCH.resting + act, {surface:'today'});
    const macros = [['protein','Protein','p'],['carbs','Carbs','c'],['fat','Fat','f'],['fiber','Fiber','fib']].map(([k, label, f]) => {
      const v = sum(f), x = nv(k, v, {surface:'today', of:GOAL[k]});
      return {...x, key:k, label, pct:x.on ? pct(v / GOAL[k]) : '0%', color:`var(--c-${k})`, tint:`var(--t-${k})`, short:x.soft ? x.text : (x.on ? Math.round(v) + ' g' : '')};
    });
    const td = {
      eaten:tEaten,
      remaining:nv('remaining', target - eaten, {surface:'today', eaten, min, max}),
      totalsCard:tEaten.on && !tEaten.soft, softCard:tEaten.on && tEaten.soft,
      lockedCard:this.vis('calories', S) === 'delay' && !tEaten.on,
      lockTitle:P.revealOn === 'week' ? 'Saved for your weekly check' : 'Open at 9:00 pm',
      lockBody:P.revealOn === 'week' ? 'Log as much or as little as you like. Totals show up once a week.' : (warm ? 'Keep logging as you go. Nothing to check until tonight.' : 'Keep logging. Totals unlock tonight.'),
      barPct:pct(eaten / scaleMax), minPct:pct(min / scaleMax), maxPct:pct(max / scaleMax),
      rangeText:tEaten.on && !tEaten.soft ? `Range ${this.format('calories', min, {bare:true})} to ${this.format('calories', max, {bare:true})}` + (adj ? `, including ${adj} from activity` : '') : '',
      energy:tEnergy, energyShort:nv('energyOut', WATCH.resting + act, {surface:'today', bare:true}),
      energyDetail:tEnergy.on ? `Everyday movement ${WATCH.movement} · workouts ${WATCH.workout} · resting ${WATCH.resting.toLocaleString('en-US')}` : '',
      macros, macrosAny:macros.some(m => m.on), macrosCard:macros.some(m => m.on) && !(tEaten.on && !tEaten.soft),
      energyCard:tEnergy.on && !(tEaten.on && !tEaten.soft),
      mealsHero:!tEaten.on && P.today !== 'rhythm' && P.today !== 'explorer',
      targetText:tEaten.on && !tEaten.soft ? this.format('calories', target, {bare:true}) : '',
      arcs:(() => {
        if (!(tEaten.on && !tEaten.soft)) return [];
        const C = 2 * Math.PI * 40, frac = Math.min(1, eaten / target);
        const parts = [['protein', sum('p') * 4], ['carbs', sum('c') * 4], ['fat', sum('f') * 9]];
        const tot = parts.reduce((a, x) => a + x[1], 0) || 1; let acc = 0;
        return parts.map(([k, v]) => { const len = Math.max(0, frac * C * v / tot - 6); const a = {color:`var(--c-${k})`, dash:`${len.toFixed(1)} ${C.toFixed(1)}`, offset:(-acc).toFixed(1)}; acc += frac * C * v / tot; return a; });
      })(),
      rhythm:P.today === 'rhythm', explorer:P.today === 'explorer', showMeals:P.today !== 'rhythm',
      showMealCount:!!P.mealCount, mealCount:`${doneMeals} of ${st.meals.length}`,
    };
    const mealTiles = st.meals.map(m => { const done = m.items.length > 0 || st.ate[m.name];
      return {...mc(m.name), name:m.name, state:done ? 'check_circle' : 'add_circle', sub:done ? (m.items.length ? `${m.items.length} ${m.items.length === 1 ? 'item' : 'items'} · ${m.time}` : 'Ate, no details') : 'Tap to add', onClick:() => this.openQuick(m.name)}; });
    const meals = st.meals.map(m => ({
      ...mc(m.name), name:m.name, lower:m.name.toLowerCase(), time:m.items.length ? m.time : '', empty:!m.items.length,
      cal:m.items.length ? nv('calories', m.items.reduce((a, i) => a + i.kcal, 0), {surface:'today'}) : OFF,
      items:m.items.map(i => ({name:i.name, sub:[i.amount, i.precision].filter(Boolean).join(' · '), cal:nv('calories', i.kcal, {surface:'today'})})),
      onAdd:() => this.openQuick(m.name), onMenu:() => this.setState({sheet:'mealMenu', menuMeal:m.name}),
      note:(st.mealNotes[m.name] || {}).text || '', hasNote:!!st.mealNotes[m.name], noteIcon:(st.mealNotes[m.name] || {}).voice ? 'mic' : 'edit_note',
      ...(() => { const f = st.mealFeel[m.name]; const arr = f ? [f.hunger, f.full, ...(f.mood || [])].filter(Boolean) : []; return {feel:arr, hasFeel:arr.length > 0}; })(),
      canCopy:!m.items.length && !!(YESTERDAY[m.name] || []).length,
      onCopy:() => { this.setState({slot:m.name}); this.addItems((YESTERDAY[m.name] || []).map(x => ({...x})), m.name); },
    }));
    const di = dayInfo(st.day), closed = st.day < 0 || !!st.closed[st.day];
    const day = {
      label:di.label, date:di.date, atEnd:st.day >= 6, nextFg:st.day >= 6 ? 'rgba(255,255,255,.3)' : '#fff',
      openCal:() => { const d = new Date(TODAY); d.setDate(d.getDate() + st.day); this.setState({sheet:'cal', calMonth:(d.getFullYear() - 2026) * 12 + d.getMonth() - 9}); }, cantClose:st.day > 0,
      prev:() => this.setDay(st.day - 1), next:() => { if (st.day < 6) this.setDay(st.day + 1); }, toToday:() => { if (st.day !== 0) this.setDay(0); },
      canClose:st.day <= 0, closeAria:closed ? 'Day closed' : 'Close the day',
      checkBg:closed ? '#fff' : 'rgba(255,255,255,.14)', checkFg:closed ? 'var(--hero)' : '#fff', checkFill:closed ? '1' : '0',
    };
    const rhythm = st.meals.map(m => {
      const done = m.items.length > 0 || st.ate[m.name];
      return {name:m.name, sub:done ? (m.items.length ? `Logged ${m.time}` : 'Ate, no details') : m.planned, pending:!done, mark:done ? '✓' : '', markBg:done ? mc(m.name).color : 'transparent',
        onAte:() => this.setState(s => ({ate:{...s.ate, [m.name]:true}})), onLog:() => this.openQuick(m.name)};
    });
    const extraNew = all.filter(i => i.isNew).map(i => ({name:i.name, when:'Today'}));
    const newFoods = [...extraNew, {name:'Poutine', when:'Sat'}, {name:'Rice pudding', when:'Thu'}, {name:'Chicken kabsa', when:'Oct 20'}];

    // Log
    const q = st.query.trim().toLowerCase();
    const results = allFoods.filter(f => !q || f.name.toLowerCase().includes(q) || f.tag.toLowerCase().includes(q)).map(f => {
      const u = f.units[0];
      return {name:f.name, sub:['1 ' + u.k, f.tag].filter(Boolean).join(' · '), cal:nv('calories', f.per.kcal * u.g / 100, {surface:'search'}),
        onClick:() => this.setState({sheet:'add', afReturn:false, foodId:f.id, unitIdx:0, qty:1, precision:'Estimate', hunger:null, fullness:null})};
    });
    const f = allFoods.find(x => x.id === st.foodId) || FOODS[0], u = f.units[st.unitIdx] || f.units[0];
    const grams = st.qty * u.g, k = grams / 100;
    const afItem = it(f.name, u.k === 'g' ? `${st.qty} g` : `${st.qty} ${u.k}`, st.precision, f.per.kcal * k, f.per.p * k, f.per.c * k, f.per.f * k, f.per.fib * k);
    const nutr = [
      {...nv('calories', afItem.kcal, {surface:'addSheet'}), key:'calories', label:'Energy'},
      {...nv('protein', afItem.p, {surface:'addSheet'}), key:'protein', label:'Protein'},
      {...nv('fiber', afItem.fib, {surface:'addSheet'}), key:'fiber', label:'Fiber'},
      {...nv('micros', f.per.ca * k, {surface:'addSheet'}), key:'micros', label:'Calcium'},
    ];
    const af = {
      name:f.name, tag:f.tag, hasTag:!!f.tag, amount:afItem.amount,
      precision:this.seg([{k:'Weighed',label:'Weighed'},{k:'Estimate',label:'Estimated'},{k:'Named',label:'Just the name'}], st.precision, v => this.setState({precision:v})),
      units:f.units.map((x, i) => { const a = i === st.unitIdx; return {label:x.k, bd:a ? 'var(--accent)' : 'var(--line)', bg:a ? 'var(--accent)' : 'var(--surface)', fg:a ? 'var(--on-accent)' : 'var(--ink)', onClick:() => this.setState({unitIdx:i, qty:x.k === 'g' ? Math.round(u.g * st.qty) : Math.max(.5, Math.round(grams / x.g * 2) / 2)})}; }),
      inc:() => this.setState(s => ({qty:+(s.qty + (u.k === 'g' ? 10 : .5)).toFixed(1)})),
      dec:() => this.setState(s => ({qty:Math.max(u.k === 'g' ? 10 : .5, +(s.qty - (u.k === 'g' ? 10 : .5)).toFixed(1))})),
      nutr, anyNutrition:nutr.some(n => n.on), tags:S.askTags,
      hunger:this.chips(['Very hungry','Peckish','Not hungry'], o => st.hunger === o, o => this.setState(s => ({hunger:s.hunger === o ? null : o}))),
      fullness:this.chips(['Still hungry','Satisfied','Very full'], o => st.fullness === o, o => this.setState(s => ({fullness:s.fullness === o ? null : o}))),
      skipTags:() => { this.update({askTags:false}); this.flash('Okay, no more hunger questions. You can turn them back on in Reminders.'); },
      addLabel:st.afReturn ? 'Pick this amount' : 'Add to ' + slotLower,
      add:() => { const item = {...afItem, isNew:!KNOWN.has(f.id)}; if (st.afReturn) { this.setState(s => ({basket:[...s.basket.filter(b => b.name !== item.name), item], sheet:'quick', afReturn:false})); return; } this.addItems([item], st.slot, item.isNew); },
    };
    const photo = {
      ready:!st.photoEst, estimated:st.photoEst, onEstimate:() => this.setState({photoEst:true}),
      items:st.photoItems.map((x, i) => ({name:x.name, amount:x.amount, cal:nv('calories', x.kcal, {surface:'photo'}),
        onName:e => { const v = e.target.value; this.setState(s => ({photoItems:s.photoItems.map((y, j) => j === i ? {...y, name:v} : y)})); },
        onAmount:e => { const v = e.target.value; this.setState(s => ({photoItems:s.photoItems.map((y, j) => j === i ? {...y, amount:v} : y)})); },
        onRemove:() => this.setState(s => ({photoItems:s.photoItems.filter((y, j) => j !== i)}))})),
      onAdd:() => { this.addItems(st.photoItems.map(x => ({...x, precision:'Estimate'}))); this.setState({photoEst:false, photoItems:PHOTO.map(x => ({...x}))}); },
    };
    const quick = {
      parsed:st.quickParsed, canNumber:this.vis('calories', S) !== 'hide',
      items:QUICK.map(x => ({name:x.name, sub:`${x.amount} · Estimate`, cal:nv('calories', x.kcal, {surface:'quick'})})),
      onAdd:() => { this.addItems(QUICK.map(x => ({...x}))); this.setState({quickParsed:false}); },
      onAddNum:() => { const n = parseInt(st.quickNum, 10); if (n > 0) { this.addItems([it('Quick add', '', 'Named', n)]); this.setState({quickNum:''}); } },
    };
    const saved = st.savedMeals.map(s => ({name:s.name, sub:s.items.map(x => x.name.split(',')[0]).join(', '), cal:nv('calories', s.items.reduce((a, x) => a + x.kcal, 0), {surface:'saved'}), onAdd:() => this.addItems(s.items.map(x => ({...x})))}));

    // Day close
    const dEaten = nv('calories', eaten, {surface:'dayclose', of:target, bare:true});
    const dTotals = dEaten.on && !dEaten.soft;
    const dc = {
      title:warm ? 'How today went' : 'Close the day',
      eaten:dEaten, totals:dTotals, soft:dEaten.on && dEaten.soft, noNumbers:!dEaten.on,
      revealNote:dTotals && this.vis('calories', S) === 'delay',
      rangeText:dTotals ? `Your range was ${this.format('calories', min, {bare:true})} to ${this.format('calories', max, {bare:true})}` : '',
      protein:nv('protein', sum('p'), {surface:'dayclose'}),
      bigCard:dTotals && eaten > max && S.events.bigday !== 'off',
      tomorrow:nv('calories', baseT, {surface:'dayclose'}),
      commit:S.events.bigday === 'full' && !st.committed, committed:st.committed,
      onCommit:() => this.setState({committed:true}),
      mealsLine:`${doneMeals} of ${st.meals.length} meals`,
      doneLabel:st.day === 0 ? 'Done for today' : 'Done',
      onDone:() => { this.setState(s => ({closed:{...s.closed, [s.day]:true}})); this.go('today'); },
    };

    // Week: last full week ending on or before today, by chosen start day
    const startIdx = {Mon:2, Sun:1, Sat:0}[S.weekStart];
    const win = DAYS.slice(startIdx, startIdx + 7).map(d => d.today ? {...d, kcal:eaten, meals:doneMeals} : d);
    const tracked = win.filter(d => d.kcal != null);
    const avg = tracked.reduce((a, d) => a + d.kcal, 0) / tracked.length;
    const wAvg = nv('calories', avg, {surface:'week'});
    const wk = {
      range:`Oct ${win[0].n} to ${win[6].n}`, bars:wAvg.on && !wAvg.soft, avg:wAvg.on ? {text:'Average ' + wAvg.text} : OFF,
      bandLo:pct(GOAL.min / 3200), bandH:pct((GOAL.max - GOAL.min) / 3200),
      days:win.map(d => ({label:d.d.slice(0, 2), h:d.kcal == null || !wAvg.on ? '0%' : pct(d.kcal / 3200), fill:d.today ? 'var(--c-fat)' : 'var(--accent)',
        dots:['Breakfast','Lunch','Snack','Dinner'].map((n, i) => i < (d.meals || 0) ? mc(n).color : 'var(--track)')})),
      recap:[
        {label:'Breakfast', value:`${win.filter(d => BREAKFAST[d.n]).length} of 7 days`},
        {label:'New foods', value:String(win.filter(d => d.nf).length + extraNew.length)},
        {label:'Days logged', value:`${win.filter(d => d.meals).length} of 7`},
      ],
      starts:this.seg([{k:'Mon',label:'Monday'},{k:'Sat',label:'Saturday'},{k:'Sun',label:'Sunday'}], S.weekStart, v => this.update({weekStart:v})),
    };

    // Modes
    const presets = ORDER.map(k => ({label:PRESETS[k].label, for:PRESETS[k].for, ...this.radio(S.mode === k && !Object.keys(S.overrides).length), onClick:() => this.requestChange({mode:k, overrides:{}}, PRESETS[k].label)}));
    const matrix = M.map(m => ({label:METRIC_LABEL[m], opts:['show','soften','delay','hide'].map(v => ({aria:`${METRIC_LABEL[m]}: ${v}`, ...this.radio(this.vis(m, S) === v), onClick:() => this.requestChange({overrides:{...S.overrides, [m]:v}}, METRIC_LABEL[m])}))}));
    const goalRange = nv('calories', min, {surface:'modes'});
    const modeLabel = PRESETS[S.mode].label + (Object.keys(S.overrides).length ? ', adjusted' : '');

    // Weight
    const wvis = this.vis('weight', S);
    const wN = {'1W':7, '1M':28, '3M':90}[st.wRange] || 28, emaAll = []; WEIGHTS.forEach((w, i) => emaAll.push(i ? emaAll[i - 1] * .8 + w * .2 : w));
    const Wr = WEIGHTS.slice(-wN), ema = emaAll.slice(-wN);
    const wLo = Math.min(...Wr) - .3, wHi = Math.max(...Wr) + .3;
    const X = i => 5 + i / (wN - 1) * 300, Y = w => 120 - (w - wLo) / (wHi - wLo) * 110;
    const wNow = nv('weight', WEIGHTS[WEIGHTS.length - 1], {surface:'weight'});
    const wv = {
      opts:this.seg([{k:'hide',label:'Hidden'},{k:'soften',label:'Trend only'},{k:'show',label:'Numbers'}], wvis === 'delay' ? 'soften' : wvis, v => this.requestChange({overrides:{...S.overrides, weight:v}}, 'Weight')),
      now:wNow.on && !wNow.soft ? wNow : OFF,
      avg:nv('weight', WEIGHTS.slice(-7).reduce((a, b) => a + b, 0) / 7, {surface:'weight'}),
      ranges:this.seg([{k:'1W',label:'1W'},{k:'1M',label:'1M'},{k:'3M',label:'3M'}], st.wRange, v => this.setState({wRange:v})), rangeLabel:{'1W':'Last 7 days','1M':'Last 4 weeks','3M':'Last 3 months'}[st.wRange],
      chart:wNow.on, blind:!wNow.on,
      trendPath:ema.map((w, i) => (i ? 'L' : 'M') + X(i).toFixed(1) + ' ' + Y(w).toFixed(1)).join(' '),
      dotsPath:wNow.on && !wNow.soft ? Wr.map((w, i) => `M${X(i).toFixed(1)} ${Y(w).toFixed(1)}h0`).join(' ') : '',
      caption:wNow.soft ? 'Smoothed over about a week, so day-to-day water swings barely move the line.' : 'Dots are daily weigh-ins. The line is the smoothed trend.',
      inputType:wNow.on && !wNow.soft ? 'text' : 'password',
      savedText:wNow.on && !wNow.soft ? 'Saved.' : 'Saved. It stays out of view.',
    };

    // Integrations
    const ig = {
      energyOn:tEnergy.on, energyOff:!tEnergy.on,
      rows:[['Everyday movement', WATCH.movement], ['Workouts', WATCH.workout], ['Resting', WATCH.resting]].map(([label, v]) => ({label, v:nv('energyOut', v, {surface:'integrations'})})),
      exercise:[{k:'all', label:'All of it', desc:'Workouts and movement raise your target.'}, {k:'some', label:'Some', desc:'Half counts, since watch estimates run high.'}, {k:'none', label:'None', desc:'Your target stays put. Eat more when you are hungry.'}]
        .map(o => ({...o, ...this.radio(S.exercise === o.k), onClick:() => this.update({exercise:o.k})})),
    };
    const sources = [{name:'Health Connect', sub:'Resting and active energy, steps, workouts', status:'Connected'}, {name:'Wear OS watch', sub:'Quick-log saved meals from your wrist', status:'On'}, {name:'Apple Health', sub:'For the iPhone app', status:'iPhone only'}];

    // Reminders
    const events = [
      {k:'bigday', label:'After a big day', desc:'A calm note, no plan to cut back. Number modes only.'},
      {k:'newfood', label:'New foods', desc:'The first time you log something.'},
      {k:'recap', label:'Weekly recap', desc:'Sunday evening, with or without numbers.'},
      {k:'reminders', label:'Meal reminders', desc:'How noticeable they are.'},
    ].map(e => ({...e, opts:this.seg([{k:'off',label:'Off'},{k:'subtle',label:'Subtle'},{k:'full',label:'Full'}], S.events[e.k], v => this.update({events:{...S.events, [e.k]:v}}))}));
    const times = {Breakfast:'9:00 am', Lunch:'1:00 pm', Snack:'3:30 pm', Dinner:'7:30 pm'};
    const mealRem = Object.keys(times).map(n => ({name:n, time:times[n], t:this.toggle(!!S.mealRem[n], () => this.update({mealRem:{...S.mealRem, [n]:!S.mealRem[n]}}))}));

    // Widgets
    const wgEaten = nv('calories', eaten, {surface:'widget', bare:true});
    const wg = {
      numbers:wgEaten.on && !wgEaten.soft, eaten:{text:wgEaten.text + ' kcal'}, remaining:nv('remaining', target - eaten, {surface:'widget', eaten, min, max}),
      pct:pct(eaten / scaleMax), meals:!(wgEaten.on && !wgEaten.soft),
      mealList:st.meals.map(m => (m.items.length || st.ate[m.name] ? '✓ ' : '') + m.name),
      watchNum:nv('remaining', target - eaten, {surface:'widget', eaten, min, max}), mealsDone:`${doneMeals}/${st.meals.length}`,
    };
    wg.watchMeals = !wg.watchNum.on;

    // Onboarding
    const ob = st.onb, step = st.onbStep;
    const Q = [
      null,
      {title:'Why are you tracking?', sub:'Pick any.', key:'why', multi:true, options:['Curious','Performance','Eating enough','Eating regularly','A medical need','Changing my weight','Working with a provider']},
      {title:'How do you feel about seeing numbers?', key:'feel', options:['Love them','Some are fine','Rather not','Not sure']},
      {title:'What do you want to notice?', sub:'Pick any.', key:'notice', multi:true, options:['Amounts','Nutrients','Patterns','How eating feels']},
      {title:'One optional question', sub:'This only unlocks gentler starting points. We never ask for a diagnosis.', key:'hard', options:['Numbers or tracking have been hard for me before']},
    ][step];
    const pickMode = () => {
      if (ob.feel === 'Love them') return ob.hard ? 'delayed' : 'full';
      if (ob.feel === 'Rather not') return ob.why.includes('Eating regularly') ? 'rhythm' : 'blind';
      if (ob.feel === 'Some are fine') return ob.why.includes('Eating enough') ? 'fuel' : 'nutrient';
      if (ob.notice.includes('How eating feels')) return 'mindful';
      return ob.hard ? 'blind' : 'delayed';
    };
    const chosen = st.onbPick || pickMode();
    const finish = k => { this.update({mode:k, overrides:{}}); this.go('today'); };
    const onb = {
      s0:step === 0, q:step >= 1 && step <= 4, s5:step === 5, progress:`${Math.min(step, 4)} of 4`,
      title:Q ? Q.title : '', sub:Q && Q.sub ? Q.sub : '', hasSub:!!(Q && Q.sub),
      options:Q ? Q.options.map(o => {
        const a = Q.key === 'hard' ? ob.hard : Q.multi ? ob[Q.key].includes(o) : ob[Q.key] === o;
        return {label:o, mark:a ? '✓' : '', bd:a ? 'var(--ink)' : 'var(--line)', onClick:() => this.setState(s => {
          const v = Q.key === 'hard' ? !s.onb.hard : Q.multi ? (s.onb[Q.key].includes(o) ? s.onb[Q.key].filter(x => x !== o) : [...s.onb[Q.key], o]) : o;
          return {onb:{...s.onb, [Q.key]:v}, onbPick:null};
        })};
      }) : [],
      pick:PRESETS[chosen], alts:ORDER.filter(k => k !== chosen && (ob.hard ? k !== 'full' : true)).slice(0, 4).map(k => ({label:PRESETS[k].label, onClick:() => this.setState({onbPick:k})})),
      next:() => this.setState(s => ({onbStep:Math.min(5, s.onbStep + 1)})),
      primary:step === 5 ? () => finish(chosen) : () => this.setState(s => ({onbStep:s.onbStep + 1})),
      primaryLabel:step === 0 ? 'Set it up' : step === 5 ? `Start with ${PRESETS[chosen].label}` : 'Next',
      skipAll:() => finish(S.mode),
    };

    const fr = st.friction ? {
      what:st.friction.what, waiting:st.frictionLeft > 0, btn:st.frictionLeft > 0 ? `Opens in ${st.frictionLeft}s` : 'Show them',
      btnBg:st.frictionLeft > 0 ? 'var(--track)' : 'var(--ink)', btnFg:st.frictionLeft > 0 ? 'var(--muted)' : 'var(--bg)',
      reasons:this.chips(['Curious','Working toward a goal','Worried I\'m off track','Someone asked me to'], o => st.reason === o, o => this.setState({reason:o})),
      confirm:() => { if (st.frictionLeft > 0) return; this.update(st.friction.patch); this.setState({friction:null, sheet:null}); this.flash('Saved. You can hide them again any time.'); },
      cancel:() => { clearInterval(this.timer); this.setState({friction:null, sheet:null}); },
    } : {reasons:[]};

    // Nutrients detail
    const isWeek = st.nutRange === 'week', ns = isWeek ? 'week' : 'nutrients';
    const wkAvg = {kcal:2104, p:96, c:238, f:72, fib:24};
    const src = isWeek ? wkAvg : {kcal:eaten, p:sum('p'), c:sum('c'), f:sum('f'), fib:sum('fib')};
    const nm = [['calories','Energy','kcal',target,'var(--accent)'],['protein','Protein','p',GOAL.protein,'var(--c-protein)'],['carbs','Carbs','c',GOAL.carbs,'var(--c-carbs)'],['fat','Fat','f',GOAL.fat,'var(--c-fat)'],['fiber','Fiber','fib',GOAL.fiber,'var(--c-fiber)']].map(([key, label, f2, goal, color]) => {
      const x = nv(key, src[f2], {surface:ns, of:goal});
      return {...x, key, label, color, pct:x.on ? pct(src[f2] / goal) : '0%', text:x.soft ? x.text : key === 'calories' && x.on ? x.text + ' of ' + this.format('calories', goal, {bare:true}) : x.text};
    });
    const microVis = this.vis('micros', S);
    const groups = MICROS.map(g => ({title:g.title, color:g.color, rows:g.rows.map(([label, ref, d, w]) => { const v2 = isWeek ? w : d; const x = nv('micros', v2, {surface:ns, of:100}); return {label, sub:ref, pct:pct(v2 / 100), text:x.text}; })}));
    const microsOn = nv('micros', 50, {surface:ns, of:100}).on;
    const nut = {
      sub:isWeek ? 'Daily average, Oct 19 to 25' : (st.day === 0 || Math.abs(st.day) === 1 ? di.date : di.label + ', ' + di.date),
      ranges:this.seg([{k:'day',label:'Day'},{k:'week',label:'Week average'}], st.nutRange, v => this.setState({nutRange:v})),
      macros:nm, macrosAny:nm.some(r => r.on), groups, microsOn,
      microsOff:!microsOn && nm.some(r => r.on), canEnable:microVis === 'hide',
      enable:() => this.requestChange({overrides:{...S.overrides, micros:'show'}}, 'Vitamins and minerals'),
      nothing:!microsOn && !nm.some(r => r.on),
      back:() => this.go(st.prevScreen || 'today'), backLabel:st.prevScreen === 'week' ? 'Trends' : day.label,
    };
    const navItems = [['Today','today','calendar_today'],['Trends','week','insights'],['Add','add','add'],['Foods','foods','restaurant'],['More','more','more_horiz']];
    const trMacros = [['protein','Protein','p'],['carbs','Carbs','c'],['fat','Fat','f'],['fiber','Fiber','fib']].map(([key, label, f2]) => {
      const x = nv(key, wkAvg[f2], {surface:'week', of:GOAL[key]});
      return {...x, key, label, color:`var(--c-${key})`, pct:x.on ? pct(wkAvg[f2] / GOAL[key]) : '0%', short:x.soft ? x.text : x.on ? Math.round(wkAvg[f2]) + ' g' : ''};
    });
    const tr = {
      macros:trMacros, nutrients:trMacros.some(m => m.on) || microsOn,
      nutSub:microsOn ? 'Daily average, with vitamins and minerals' : 'Daily average this week',
      energy:nv('energyOut', OUT7.reduce((a, b) => a + b, 0) / 7, {surface:'week'}), energyPath:OUT7.map((v, i) => (i ? 'L' : 'M') + (5 + i * 50) + ' ' + (120 - (v - 1900) / 500 * 110).toFixed(1)).join(' '),
      weightSub:wvis === 'hide' ? 'Hidden. Tap to log a weigh-in.' : wvis === 'show' ? 'This morning' : 'Smoothed trend, 4 weeks',
    };
    const qa = (label, icon, c, onClick) => ({label, icon, color:`var(--c-${c})`, tint:`var(--t-${c})`, onClick});
    const quickActions = [
      qa('Photo','photo_camera','protein', () => this.logVia('photo')),
      qa('Describe it','mic','fiber', () => this.logVia('quick')),
      ...(this.vis('water', S) !== 'hide' ? [qa('Water','water_drop','fat', () => { this.addWater(); this.flash('Glass of water added.'); })] : []),
      qa('Saved meals','bookmark','fat', () => this.logVia('saved')),
      qa(`Yesterday's ${slotLower}`,'history','carbs', () => this.addItems((YESTERDAY[st.slot] || []).map(x => ({...x})))),
      qa('Ate, no details','check_circle','protein', () => { this.setState(s => ({ate:{...s.ate, [s.slot]:true}, sheet:null})); this.flash(`Marked ${slotLower} as eaten.`); }),
      qa('Weigh-in','monitor_weight','fiber', () => this.go('weight')),
      ...(this.vis('calories', S) !== 'hide' ? [qa('Just a number','dialpad','fat', () => this.logVia('quick'))] : [qa('Add a note','edit_note','fat', () => this.go('dayclose'))]),
    ];
    const recents = RECENT_IDS.slice(0, 4).map(id => FOODS.find(x => x.id === id)).map((f0, i) => {
      const u0 = f0.units[0], k0 = u0.g / 100;
      const item = it(f0.name, '1 ' + u0.k, 'Estimate', f0.per.kcal * k0, f0.per.p * k0, f0.per.c * k0, f0.per.f * k0, f0.per.fib * k0);
      return {name:f0.name, sub:'1 ' + u0.k, color:mc(['Breakfast','Lunch','Snack','Dinner'][i]).color, cal:nv('calories', item.kcal, {surface:'quickSheet'}), onAdd:() => this.addItems([item])};
    });
    // Logger sheet, meal builder, custom food, targets
    const meta = f0 => { const m = FOOD_META[f0.id] || ['edit_square', 'carbs']; return {icon:m[0], color:`var(--c-${m[1]})`, tint:`var(--t-${m[1]})`}; };
    const itemOf = f0 => { const u0 = f0.units[0], k0 = u0.g / 100; return it(f0.name, u0.label || '1 ' + u0.k, 'Estimate', f0.per.kcal * k0, f0.per.p * k0, f0.per.c * k0, f0.per.f * k0, f0.per.fib * k0); };
    const macroLine = (x, surface) => [['protein','P','p'],['carbs','C','c'],['fat','F','f']].map(([k, l, f2]) => { const v = nv(k, x[f2] || 0, {surface}); return v.on && !v.soft ? Math.round(x[f2] || 0) + l : ''; }).filter(Boolean).join(' ');
    const byId = id => allFoods.find(x => x.id === id);
    const foodRow = f0 => {
      const item = itemOf(f0), on = st.basket.some(b => b.name === f0.name), ml = macroLine(item, 'quickSheet');
      return {...meta(f0), name:f0.name, short:SHORT[f0.id] || f0.name.split(/[,(]/)[0].trim(), sub:[item.amount, ml].filter(Boolean).join(' · '), cal:nv('calories', item.kcal, {surface:'quickSheet'}),
        btnIcon:on ? 'check' : 'add', btnBg:on ? 'var(--accent)' : 'var(--t-accent)', btnFg:on ? 'var(--on-accent)' : 'var(--accent)', aria:(on ? 'Remove ' : 'Pick ') + f0.name,
        onToggle:() => this.setState(s => ({basket:s.basket.some(b => b.name === f0.name) ? s.basket.filter(b => b.name !== f0.name) : [...s.basket, {...item, isNew:!KNOWN.has(f0.id)}]})),
        onOpen:() => this.setState({sheet:'add', afReturn:true, foodId:f0.id, unitIdx:0, qty:1, precision:'Estimate', hunger:null, fullness:null})};
    };
    const lq = st.lq.trim().toLowerCase();
    const lgResults = lq ? allFoods.filter(f0 => f0.name.toLowerCase().includes(lq) || (f0.tag || '').toLowerCase().includes(lq)).map(foodRow) : [];
    const lgSum = [['calories','Energy',eaten,target], ...[['protein','Protein','p'],['carbs','Carbs','c'],['fat','Fat','f']].map(([k, l, f2]) => [k, l, sum(f2), GOAL[k]])].map(([k, label, v, goal]) => {
      const x = nv(k, v, {surface:'quickSheet', of:goal, bare:true});
      return {on:x.on, key:k, label, color:k === 'calories' ? 'var(--accent)' : `var(--c-${k})`, pct:x.on ? pct(v / goal) : '0%',
        text:!x.on ? '' : x.soft ? x.text : k === 'calories' ? `${x.text} / ${this.format('calories', goal, {bare:true})}` : `${Math.round(v)} / ${goal} g`};
    });
    const lg = {
      q:st.lq, onQ:e => this.setState({lq:e.target.value}), searching:!!lq, browse:!lq, results:lgResults, noResults:!!lq && !lgResults.length,
      sum:lgSum, sumNums:lgSum.some(x => x.on), sumMeals:!lgSum.some(x => x.on),
      mealDots:st.meals.map(m => { const done = m.items.length > 0 || st.ate[m.name]; return {name:m.name, icon:done ? 'check_circle' : 'radio_button_unchecked', color:done ? mc(m.name).color : 'var(--muted)'}; }),
      favs:FAVS.map(byId).map(foodRow), picksTitle:PICKS[st.slot] ? `Usual at ${slotLower}` : 'Suggested', picks:(PICKS[st.slot] || FAVS.slice(0, 3)).map(byId).map(foodRow),
      recent:[...st.customFoods.map(x => x.id), ...RECENT_IDS].slice(0, 6).map(byId).filter(Boolean).map(foodRow),
      hasBasket:st.basket.length > 0, noBasket:!st.basket.length,
      basketCount:`${st.basket.length} picked`, basketNames:st.basket.map(b => b.name.split(/[,(]/)[0].trim()).join(', '),
      basketCal:nv('calories', st.basket.reduce((a, b) => a + (b.kcal || 0), 0), {surface:'quickSheet'}),
      onScan:() => this.setState({sheet:'add', afReturn:true, foodId:'yogurt', unitIdx:0, qty:1, precision:'Weighed'}),
      commit:() => { const items = st.basket.map(b => ({...b})), n = items.length; this.setState({basket:[], lq:''}); this.addItems(items, st.slot, false); this.flash(`Added ${n === 1 ? items[0].name.split(/[,(]/)[0] : n + ' foods'} to ${slotLower}.`); },
    };
    const mbItems = st.mb.items, mbTot = k => mbItems.reduce((a, x) => a + (x[k] || 0), 0), mbq = st.mb.q.trim().toLowerCase();
    const mbCan = !!st.mb.name.trim() && mbItems.length > 0, mbMacros = macroLine({p:mbTot('p'), c:mbTot('c'), f:mbTot('f')}, 'builder');
    const mb = {
      name:st.mb.name, onName:e => { const v = e.target.value; this.setState(s => ({mb:{...s.mb, name:v}})); },
      q:st.mb.q, onQ:e => { const v = e.target.value; this.setState(s => ({mb:{...s.mb, q:v}})); },
      slots:this.chips(['Breakfast','Lunch','Snack','Dinner'], o => o === st.mb.slot, o => this.setState(s => ({mb:{...s.mb, slot:o}}))),
      items:mbItems.map((x, i) => ({name:x.name, sub:[x.amount, macroLine(x, 'builder')].filter(Boolean).join(' · '), cal:nv('calories', x.kcal, {surface:'builder'}),
        onRemove:() => this.setState(s => ({mb:{...s.mb, items:s.mb.items.filter((y, j) => j !== i)}}))})),
      empty:!mbItems.length, hasItems:mbItems.length > 0,
      total:nv('calories', mbTot('kcal'), {surface:'builder'}), totalSub:[`${mbItems.length} ${mbItems.length === 1 ? 'food' : 'foods'}`, mbMacros].filter(Boolean).join(' · '),
      picks:allFoods.filter(f0 => !mbq || f0.name.toLowerCase().includes(mbq)).slice(0, 6).map(f0 => ({...meta(f0), name:f0.name, sub:f0.units[0].label || '1 ' + f0.units[0].k,
        onAdd:() => this.setState(s => ({mb:{...s.mb, items:[...s.mb.items, itemOf(f0)]}}))})),
      saveBg:mbCan ? 'var(--accent)' : 'var(--track)', saveFg:mbCan ? 'var(--on-accent)' : 'var(--muted)',
      save:() => { if (!mbCan) { this.flash(st.mb.name.trim() ? 'Add at least one food first.' : 'Give the meal a name first.'); return; }
        this.setState(s => ({savedMeals:[{name:s.mb.name.trim(), items:s.mb.items}, ...s.savedMeals], mb:{...MB0}})); this.go('foods'); this.flash('Meal saved. It\'s in the + sheet under Saved meals.'); },
    };
    const cfSet = k => e => { const v = e.target.value; this.setState(s => ({cf:{...s.cf, [k]:v}})); };
    const cfFields = [['calories','Energy','kcal','kcal'],['protein','Protein','p','g'],['carbs','Carbs','c','g'],['fat','Fat','f','g'],['fiber','Fiber','fib','g']]
      .filter(([m]) => this.vis(m, S) !== 'hide').map(([m, label, k, unit]) => ({key:m, label, unit, value:st.cf[k], onChange:cfSet(k)}));
    const cfCan = !!st.cf.name.trim();
    const cf = {
      name:st.cf.name, onName:cfSet('name'), brand:st.cf.brand, onBrand:cfSet('brand'), amt:st.cf.amt, onAmt:cfSet('amt'),
      units:this.chips(['serving','g','piece','cup'], o => o === st.cf.unit, o => this.setState(s => ({cf:{...s.cf, unit:o}}))),
      fields:cfFields, hasFields:cfFields.length > 0, labelOnly:!cfFields.length, partial:cfFields.length > 0 && cfFields.length < 5,
      onLabel:() => this.flash('Label photo saved. Nothing from it will show in this mode.'),
      saveBg:cfCan ? 'var(--accent)' : 'var(--track)', saveFg:cfCan ? 'var(--on-accent)' : 'var(--muted)',
      save:() => { const c = st.cf; if (!cfCan) { this.flash('Give the food a name first.'); return; } const n = k => parseFloat(c[k]) || 0;
        const food = {id:'custom' + Date.now(), name:c.name.trim(), tag:c.brand.trim(), per:{kcal:n('kcal'), p:n('p'), c:n('c'), f:n('f'), fib:n('fib'), ca:0}, units:[{k:'serving', label:`${c.amt || 1} ${c.unit}`, g:100}]};
        this.setState(s => ({customFoods:[food, ...s.customFoods], cf:{...CF0}})); this.go('foods'); this.flash(`${food.name} saved. It shows up in search now.`); },
    };
    const myFoods = st.customFoods.map(f0 => { const item = itemOf(f0); return {name:f0.name, sub:[item.amount, f0.tag].filter(Boolean).join(' · '), cal:nv('calories', item.kcal, {surface:'foods'}), onAdd:() => this.addItems([item])}; });
    const fmtK = v => this.format('calories', v, {bare:true});
    const canLower = baseT - 50 >= (GOAL_FLOOR_KCAL ?? GOAL.target);
    const tgMacros = [['protein','Protein',4],['carbs','Carbs',4],['fat','Fat',9]].map(([k, label, kc]) => {
      const g = st.macroMode === 'ratio' ? Math.round(GOAL[k] * baseT / GOAL.target) : GOAL[k], x = nv(k, g, {surface:'targets'});
      return {on:x.on && !x.soft, key:k, label, color:`var(--c-${k})`, grams:g + ' g', pct:Math.round(g * kc / baseT * 100) + '%'};
    });
    const tg = {
      show:this.vis('calories', S) === 'show', hidden:this.vis('calories', S) !== 'show',
      rangeText:`${fmtK(GOAL.min + st.goalAdj)} to ${fmtK(GOAL.max + st.goalAdj)} kcal`, targetText:`Aim for about ${fmtK(baseT)} kcal, before activity`,
      lower:() => { if (canLower) this.setState(s => ({goalAdj:s.goalAdj - 50})); else this.flash('Going lower needs a floor set with a dietitian first.'); },
      raise:() => this.setState(s => ({goalAdj:s.goalAdj + 50})),
      lowerBg:canLower ? 'var(--soft)' : 'transparent', lowerFg:canLower ? 'var(--ink)' : 'var(--muted)',
      floorNote:canLower ? 'Moves 50 kcal at a time.' : "Raising is always open. Going below where you started needs a floor set with a dietitian, so it's off for now.",
      modes:this.seg([{k:'ratio',label:'Ratios'},{k:'grams',label:'Grams'}], st.macroMode, v => this.setState({macroMode:v})),
      modeNote:st.macroMode === 'ratio' ? 'Grams follow your energy target, so the split stays the same.' : 'Grams stay put when your energy target changes.',
      macros:tgMacros, macrosAny:tgMacros.some(m => m.on), exLabel:{all:'All of it counts', some:'Some, half counts', none:'None'}[S.exercise],
    };
    // Swipe summary
    const C35 = 2 * Math.PI * 35;
    const arcsFrom = (parts, frac) => { const tot = parts.reduce((a, x) => a + x[1], 0) || 1; let acc = 0;
      return parts.map(([color, v]) => { const full = frac * C35 * v / tot, len = Math.max(0, full - (frac >= 1 ? 6 : 0)); const a = {color, dash:`${len.toFixed(1)} ${C35.toFixed(1)}`, offset:(-acc).toFixed(1)}; acc += full; return a; }); };
    const calShown = tEaten.on && !tEaten.soft, eShort = td.energyShort;
    const MAXK = Math.min(1, eaten / target);
    const ringModes = ['target', ...(this.vis('remaining', S) === 'show' ? ['remaining'] : []), ...(eShort.on && !eShort.soft ? ['balance'] : [])];
    const rm = ringModes.includes(S.ringMode) ? S.ringMode : 'target';
    const ringArc = f => [{color:'#fff', dash:`${(Math.max(0, Math.min(1, f)) * C35).toFixed(1)} ${C35.toFixed(1)}`, offset:'0'}];
    const outK = WATCH.resting + act, inRange = eaten >= min && eaten <= max;
    const ringThree = rm === 'remaining' ? {on:calShown, key:'remaining', label:'Remaining', value:eaten < min ? this.format('calories', target - eaten, {bare:true}) : inRange ? 'On' : 'Past', unit:eaten < min ? 'kcal' : inRange ? 'target' : 'range', arcs:ringArc(1 - eaten / target)}
      : rm === 'balance' ? {on:calShown, key:'calories', label:'Balance', value:(eaten < outK ? '−' : '+') + this.format('calories', Math.abs(eaten - outK), {bare:true}), unit:'kcal', arcs:ringArc(eaten / outK)}
      : {on:calShown, key:'calories', label:'Target', value:td.targetText, unit:'kcal', arcs:ringArc(MAXK)};
    const scoreRows = [['All targets', null], ['Vitamins', MICROS[0].rows.map(r => r[0])], ['Minerals', MICROS[1].rows.map(r => r[0])], ['Bone health', ['Calcium','Vitamin D','Vitamin K','Magnesium']], ['Immune support', ['Vitamin A','Vitamin C','Vitamin D','Zinc']], ['Energy and metabolism', ['Vitamin B12','Iron','Magnesium','Folate']]];
    const microDay = Object.fromEntries(MICROS.flatMap(g => g.rows.map(r => [r[0], Math.min(100, r[2])])));
    const scores = scoreRows.map(([label, keys], i) => { const ks = keys || Object.keys(microDay); const v = ks.reduce((a, k) => a + microDay[k], 0) / ks.length; const x = nv('micros', v, {surface:'today', of:100});
      return {label, pct:pct(v / 100), text:x.text, weight:i ? '600' : '800'}; });
    const swGo = i => { const el = this.swRef.current; if (el) el.scrollLeft = i * el.clientWidth; this.setState({swIdx:i}); };
    const sw = {
      ref:this.swRef, title:['Energy summary','Macros','Nutrition scores'][st.swIdx] || 'Energy summary', hint:st.swIdx === 0 ? 'Swipe for more' : '',
      onScroll:e => { const el = e.currentTarget; const i = Math.round(el.scrollLeft / el.clientWidth); if (i !== this.state.swIdx) this.setState({swIdx:i}); },
      prev:() => swGo(Math.max(0, st.swIdx - 1)), next:() => swGo(Math.min(2, st.swIdx + 1)),
      dots:[0, 1, 2].map(i => ({bg:i === st.swIdx ? '#fff' : 'rgba(255,255,255,.3)', aria:`Summary page ${i + 1}`, onClick:() => swGo(i)})),
      rings:[
        {on:calShown, key:'calories', label:'Eaten', unit:'kcal', value:tEaten.text, arcs:arcsFrom([['var(--c-protein)', sum('p') * 4], ['var(--c-carbs)', sum('c') * 4], ['var(--c-fat)', sum('f') * 9]], eaten > 0 ? 1 : 0)},
        {on:eShort.on && !eShort.soft, key:'energyOut', label:'Expended', unit:'kcal', value:eShort.text, arcs:arcsFrom([['rgba(255,255,255,.92)', WATCH.resting], ['rgba(255,255,255,.6)', WATCH.movement], ['rgba(255,255,255,.36)', WATCH.workout]], 1)},
        ringThree,
      ],
      modeOn:st.swIdx === 0 && ringModes.length > 1, modeLabel:{target:'Target', remaining:'Remaining', balance:'Balance'}[rm], cycle:() => this.update({ringMode:ringModes[(ringModes.indexOf(rm) + 1) % ringModes.length]}),
      scores:scores.slice(0, 4), scoresOn:nv('micros', 50, {surface:'today', of:100}).on, canEnable:this.vis('micros', S) === 'hide',
      enable:() => this.requestChange({overrides:{...S.overrides, micros:'show'}}, 'Vitamins and minerals'),
    };
    sw.scoresOff = !sw.scoresOn;
    // Water
    const wMl = st.water[st.day] ?? (st.day < 0 ? 1500 : 0), goalMl = S.waterGoal * S.glassMl, filled = Math.floor(wMl / S.glassMl + 1e-6);
    const addGlass = () => this.addWater();
    const wfmt = x => S.waterUnit === 'oz' ? Math.round(x / 29.57) + ' fl oz' : x + ' ml';
    const wt = {
      on:this.vis('water', S) !== 'hide' && P.today !== 'explorer', text:nv('water', wMl, {surface:'today', of:goalMl, unit:S.waterUnit}),
      segs:Array.from({length:S.waterGoal}, (_, i) => i < filled ? 'var(--c-fat)' : 'var(--track)'),
      onMenu:() => this.setState({sheet:'waterMenu', waterCustom:''}),
      glassLabel:`Add a glass, ${wfmt(S.glassMl)}`, add:addGlass, addAndClose:() => { addGlass(); this.setState({sheet:null}); this.flash('Glass of water added.'); },
      custom:st.waterCustom, onCustom:e => this.setState({waterCustom:e.target.value.replace(/[^\d]/g, '')}), unitLabel:S.waterUnit === 'oz' ? 'fl oz' : 'ml',
      addCustom:() => { const n = parseInt(st.waterCustom, 10); if (!n) return; this.addWater(S.waterUnit === 'oz' ? Math.round(n * 29.57) : n); this.setState({sheet:null}); this.flash('Water added.'); },
      undo:() => { this.addWater(-S.glassMl); this.setState({sheet:null}); this.flash('Removed a glass.'); }, canUndo:wMl > 0,
      settings:() => this.go('water'),
      goals:this.seg([6, 8, 10, 12].map(k => ({k, label:String(k)})), S.waterGoal, v => this.update({waterGoal:v})),
      sizes:this.seg([200, 250, 330, 500].map(k => ({k, label:wfmt(k)})), S.glassMl, v => this.update({glassMl:v})),
      units:this.seg([{k:'ml', label:'ml'}, {k:'oz', label:'fl oz'}], S.waterUnit, v => this.update({waterUnit:v})),
      showT:this.toggle(this.vis('water', S) !== 'hide', () => this.requestChange({overrides:{...S.overrides, water:this.vis('water', S) !== 'hide' ? 'hide' : 'show'}}, 'Water')),
      remT:this.toggle(!!S.waterRem, () => this.update({waterRem:!S.waterRem})),
      goalText:`${S.waterGoal} glasses, ${wfmt(goalMl)} a day`,
    };
    // Fasting
    const fastOk = FAST_OK.includes(S.mode);
    const plan = FAST_PLANS[S.fastPlan] || FAST_PLANS['14:10'];
    const fastOn = fastOk && S.fasting;
    const fs = {
      allowed:fastOk, blocked:!fastOk, on:fastOn, dim:S.fasting ? '1' : '.45',
      blockedBody:'Long gaps between meals tend to work against the goals of this mode. If regular meals are the aim, meal reminders help with that instead.',
      toggle:this.toggle(!!S.fasting, () => this.update({fasting:!S.fasting})),
      todayToggle:this.toggle(!!S.fastToday, () => { if (S.fasting) this.update({fastToday:!S.fastToday}); }),
      plans:this.seg(Object.keys(FAST_PLANS).map(k => ({k, label:k})), S.fastPlan, v => { if (S.fasting) this.update({fastPlan:v}); }),
      windowText:`Eat between ${plan.open} and ${plan.close}.`,
      icon:st.fastActive ? 'hourglass_top' : 'restaurant',
      title:st.fastActive ? 'Fasting' : 'Eating window open',
      big:st.fastActive ? '13h 40m' : `Until ${plan.close}`,
      sub:st.fastActive ? `Since ${plan.close} last night` : `Your window runs ${plan.open} to ${plan.close}.`,
      action:st.fastActive ? () => { this.setState({fastActive:false}); this.flash('Fast ended.'); } : () => { this.setState({fastActive:true}); this.flash('Fast started. Logging food ends it.'); },
      actionLabel:st.fastActive ? 'End fast' : 'Start a fast',
    };
    fs.todayCard = fastOn && S.fastToday && st.day === 0;
    // Calendar
    const calBase = new Date(2026, 9 + st.calMonth, 1);
    const daysIn = new Date(calBase.getFullYear(), calBase.getMonth() + 1, 0).getDate();
    const nOf = d => Math.round((new Date(calBase.getFullYear(), calBase.getMonth(), d) - TODAY) / 86400000);
    const cells = [...Array(calBase.getDay()).fill(0), ...Array.from({length:daysIn}, (_, i) => i + 1)].map(d => {
      if (!d) return {vis:'hidden', label:'', aria:'', bg:'transparent', fg:'transparent', bd:'transparent', dot:'transparent', onClick:() => {}};
      const n = nOf(d), ok = n <= 6, sel = n === st.day;
      const logged = n < 0 ? !(n === -7 || (n < -9 && (-n) % 6 === 0)) : n === 0 ? doneMeals > 0 : false;
      return {vis:'visible', label:String(d), aria:calBase.toLocaleDateString('en-US', {month:'long'}) + ' ' + d, bg:sel ? 'var(--accent)' : 'transparent', fg:sel ? 'var(--on-accent)' : ok ? 'var(--ink)' : 'var(--line)', bd:n === 0 && !sel ? 'var(--accent)' : 'transparent',
        dot:logged ? 'var(--c-fat)' : 'transparent', onClick:() => { if (!ok) return; this.setDay(n); this.setState({sheet:null}); }};
    });
    const cal = {
      title:calBase.toLocaleDateString('en-US', {month:'long', year:'numeric'}), week:['S','M','T','W','T','F','S'], cells,
      prev:() => this.setState(s => ({calMonth:Math.max(-12, s.calMonth - 1)})), next:() => this.setState(s => ({calMonth:Math.min(0, s.calMonth + 1)})), nextFg:st.calMonth >= 0 ? 'var(--line)' : 'var(--ink)',
      toToday:() => { this.setDay(0); this.setState({sheet:null, calMonth:0}); },
    };
    // Menus
    const mi = (label, icon, fn, keepOpen) => ({label, icon, iconFg:'var(--muted)', onClick:() => { if (!keepOpen) this.setState({sheet:null}); fn(); }});
    const prevMeals = st.dayStore[st.day - 1] || mealsFor(st.day - 1);
    const dayItems = [
      mi(st.day === 0 ? 'Close the day' : 'Close this day', 'check_circle', () => this.go('dayclose')),
      mi("This day's nutrients", 'monitoring', () => { this.setState({nutRange:'day'}); this.go('nutrients'); }),
      mi('Copy previous day', 'content_copy', () => { this.setState(s => ({meals:s.meals.map(m => { const pm = prevMeals.find(x => x.name === m.name); return pm && pm.items.length ? {...m, time:m.time || pm.time, items:[...m.items, ...pm.items.map(x => ({...x}))]} : m; })})); this.flash('Copied the previous day in.'); }),
      mi('Copy this day to tomorrow', 'file_copy', () => { if (st.day >= 6) return; this.setState(s => ({dayStore:{...s.dayStore, [s.day + 1]:s.meals.map(m => ({...m, items:m.items.map(x => ({...x}))}))}})); this.flash('Copied. It will be waiting on the next day.'); }),
      mi('Add a note for the day', 'edit_note', () => this.go('dayclose')),
      mi(groupsOn ? 'Turn off meal groups' : 'Turn on meal groups', 'view_agenda', () => this.update({mealGroups:!groupsOn})),
      mi(wt.on ? 'Hide water' : 'Show water', 'water_drop', () => this.requestChange({overrides:{...S.overrides, water:wt.on ? 'hide' : 'show'}}, 'Water')),
      ...(fastOk ? [mi('Fasting', 'schedule', () => this.go('fasting'))] : []),
      st.clearAsk ? {...mi('Tap again to clear', 'delete', () => { this.setState(s => ({meals:s.meals.map(m => ({...m, items:[]})), ate:{}, loose:[], clearAsk:false})); this.flash('Cleared this day.'); }), iconFg:'var(--c-protein)'}
        : mi('Clear this day', 'delete', () => this.setState({clearAsk:true}), true),
    ];
    const addMenuItems = [
      mi('Food', 'restaurant', () => this.openQuick()),
      mi('Scan a barcode', 'barcode_scanner', () => this.setState({sheet:'add', afReturn:false, foodId:'yogurt', unitIdx:0, qty:1, precision:'Weighed'})),
      mi('Photo', 'photo_camera', () => this.logVia('photo')),
      mi('Describe it', 'mic', () => this.logVia('quick')),
      ...(wt.on ? [mi('Glass of water', 'water_drop', () => { addGlass(); this.flash('Glass of water added.'); })] : []),
      mi('Weigh-in', 'monitor_weight', () => this.go('weight')),
      mi('Note', 'edit_note', () => this.go('dayclose')),
      ...(fastOn ? [mi(st.fastActive ? 'End fast' : 'Start a fast', 'schedule', fs.action)] : []),
    ];
    wk.weekDays = wk.days;
    const trR = S.trRange || '1W';
    if (trR !== '1W') { const nW = trR === '4W' ? 4 : 12;
      wk.days = WEEKLY.slice(-nW).map((v, i) => { const d = new Date(TODAY); d.setDate(d.getDate() - 7 * (nW - 1 - i)); const val = i === nW - 1 ? avg : v;
        return {label:nW === 12 && (nW - 1 - i) % 3 ? '' : (d.getMonth() + 1) + '/' + d.getDate(), h:wAvg.on ? pct(val / 3200) : '0%', fill:i === nW - 1 ? 'var(--c-fat)' : 'var(--accent)', dots:[]}; });
      wk.range = trR === '4W' ? 'Last 4 weeks' : 'Last 3 months'; wk.intakeTitle = 'Weekly average intake';
    } else wk.intakeTitle = 'Daily intake';
    const TC = S.trCards;
    const trs = {
      intake:wk.bars && TC.intake, recap:TC.recap, nutrients:tr.nutrients && TC.nutrients, expended:tr.energy.on && TC.expended, weight:TC.weight,
      ranges:this.seg([{k:'1W',label:'Week'},{k:'4W',label:'4 weeks'},{k:'3M',label:'3 months'}], trR, v => this.update({trRange:v})),
      toggles:[['intake','Intake chart',wk.bars],['recap','Recap',true],['nutrients','Nutrients',tr.nutrients],['expended','Expended',tr.energy.on],['weight','Weight',true]].filter(x => x[2])
        .map(([k, label]) => ({label, t:this.toggle(!!TC[k], () => this.update({trCards:{...TC, [k]:!TC[k]}}))})),
    };
    // Per-meal menu, notes, how it felt
    const mm = st.menuMeal || 'Dinner', mmLower = mm.toLowerCase(), mmMeal = mm === 'Uncategorized' ? {items:st.loose} : (st.meals.find(x => x.name === mm) || st.meals[0]);
    const mItem = (label, icon, fn) => ({label, icon, onClick:() => { this.setState({sheet:null}); fn(); }});
    const mealMenu = [
      mItem('Add food', 'restaurant', () => this.openQuick(mm)),
      mItem('Scan a barcode', 'barcode_scanner', () => this.setState({slot:mm, sheet:'add', afReturn:false, foodId:'yogurt', unitIdx:0, qty:1, precision:'Weighed'})),
      mItem('Photo', 'photo_camera', () => { this.setState({slot:mm}); this.logVia('photo'); }),
      mItem('Voice log', 'mic', () => this.setState({sheet:'mealNote', noteVoice:true, recording:false, recDone:false})),
      mItem('Add a note', 'edit_note', () => this.setState({sheet:'mealNote', noteVoice:false, noteDraft:(st.mealNotes[mm] || {}).voice ? '' : ((st.mealNotes[mm] || {}).text || '')})),
      mItem('How it felt', 'mood', () => this.setState({sheet:'mealFeel', feelDraft:{hunger:null, full:null, mood:[], ...(st.mealFeel[mm] || {})}})),
      ...((YESTERDAY[mm] || []).length ? [mItem(`Copy yesterday's ${mmLower}`, 'history', () => this.addItems((YESTERDAY[mm] || []).map(x => ({...x})), mm))] : []),
      mItem('Ate, no details', 'check_circle', () => { this.setState(s => ({ate:{...s.ate, [mm]:true}})); this.flash(`Marked ${mmLower} as eaten.`); }),
      ...(mmMeal.items.length ? [mItem('Save as a meal', 'bookmark_add', () => { this.setState({mb:{name:`My ${mmLower}`, slot:mm, items:mmMeal.items.map(x => ({...x})), q:''}}); this.go('mealbuilder'); })] : []),
    ];
    const note = {
      voice:!!st.noteVoice, text:!st.noteVoice, draft:st.noteDraft || '', onDraft:e => this.setState({noteDraft:e.target.value}),
      title:(st.noteVoice ? 'Voice log, ' : 'Note, ') + mmLower,
      recLabel:st.recording ? 'Recording 0:07' : st.recDone ? 'Recorded 0:07' : 'Tap to record', recBg:st.recording ? 'var(--c-protein)' : 'var(--accent)', recIcon:st.recording ? 'stop' : 'mic',
      onRec:() => this.setState(s => ({recording:!s.recording, recDone:s.recording ? true : s.recDone})),
      save:() => {
        if (st.noteVoice) { if (!st.recDone && !st.recording) { this.flash('Record something first.'); return; }
          this.setState(s => ({mealNotes:{...s.mealNotes, [mm]:{voice:true, text:'Voice note, 0:07'}}, sheet:null, recording:false, recDone:false})); this.flash(`Voice note saved to ${mmLower}.`); return; }
        const t = (st.noteDraft || '').trim();
        this.setState(s => { const n = {...s.mealNotes}; if (t) n[mm] = {voice:false, text:t}; else delete n[mm]; return {mealNotes:n, sheet:null}; });
        if (t) this.flash(`Note saved to ${mmLower}.`);
      },
    };
    const fd = st.feelDraft || {hunger:null, full:null, mood:[]};
    const pickOne = k => o => this.setState(s => ({feelDraft:{...s.feelDraft, [k]:(s.feelDraft || {})[k] === o ? null : o}}));
    const feel = {
      title:`How ${mmLower} felt`,
      hunger:this.chips(['Very hungry','Hungry','Peckish','Not hungry'], o => fd.hunger === o, pickOne('hunger')),
      full:this.chips(['Still hungry','Satisfied','Full','Very full'], o => fd.full === o, pickOne('full')),
      mood:this.chips(['Calm','Happy','Social','Rushed','Stressed','Anxious','Low'], o => (fd.mood || []).includes(o), o => this.setState(s => { const m0 = (s.feelDraft || {}).mood || []; return {feelDraft:{...s.feelDraft, mood:m0.includes(o) ? m0.filter(x => x !== o) : [...m0, o]}}; })),
      save:() => { this.setState(s => ({mealFeel:{...s.mealFeel, [mm]:s.feelDraft}, sheet:null})); this.flash('Saved. Only you see this.'); },
    };
    // Uncategorized, macro lines, health
    const macroText = items => [['protein','p','protein'],['carbs','c','carbs'],['fat','f','fat']].map(([k, f2, w]) => { const v = items.reduce((a, i) => a + (i[f2] || 0), 0), x = nv(k, v, {surface:'today'}); return x.on && !x.soft ? `${Math.round(v)} g ${w}` : ''; }).filter(Boolean).join(' · ');
    const extras = name => { const n = st.mealNotes[name], f = st.mealFeel[name], arr = f ? [f.hunger, f.full, ...(f.mood || [])].filter(Boolean) : [];
      return {note:(n || {}).text || '', hasNote:!!n, noteIcon:(n || {}).voice ? 'mic' : 'edit_note', feel:arr, hasFeel:arr.length > 0}; };
    meals.forEach(m => { const src = st.meals.find(x => x.name === m.name); m.macroText = macroText(src.items); m.hasMacros = !!m.macroText; });
    const loItems = groupsOn ? st.loose : all;
    const lo = {icon:'format_list_bulleted', color:'var(--muted)', tint:'var(--soft)', name:groupsOn ? 'Uncategorized' : 'All food', lower:'uncategorized', time:'', empty:!loItems.length,
      cal:loItems.length ? nv('calories', loItems.reduce((a, i) => a + (i.kcal || 0), 0), {surface:'today'}) : OFF,
      items:loItems.map(i => ({name:i.name, sub:[i.amount, i.precision].filter(Boolean).join(' · '), cal:nv('calories', i.kcal, {surface:'today'})})),
      onMenu:() => this.setState({sheet:'mealMenu', menuMeal:'Uncategorized'}), macroText:macroText(loItems), ...extras('Uncategorized'),
      show:!groupsOn || st.loose.length > 0};
    lo.hasMacros = !!lo.macroText;
    const HS = {steps:true, hr:true, rhr:true, hrv:false, sleep:true, vo2:false, workout:true, ...(S.healthRows || {})};
    const healthOn = S.healthToday ?? (P.today === 'full');
    const wkE = nv('energyOut', WATCH.workout, {surface:'today'});
    const HEALTH = [
      {k:'steps', label:'Steps', icon:'directions_walk', value:'8,412'},
      {k:'hr', label:'Heart rate', icon:'favorite', value:'72 bpm', sub:'Range 58 to 131'},
      {k:'rhr', label:'Resting heart rate', icon:'monitor_heart', value:'57 bpm'},
      {k:'hrv', label:'Heart rate variability', icon:'ecg_heart', value:'42 ms', sub:'Average 56, peak 90'},
      {k:'sleep', label:'Sleep', icon:'bedtime', value:'7 h 20 m', sub:'11:40 pm to 7:05 am'},
      {k:'vo2', label:'VO2 max', icon:'pulmonology', value:'41.2', sub:'mL/kg/min'},
      {k:'workout', label:'Walking', icon:'hiking', value:'63 min', sub:wkE.on && !wkE.soft ? wkE.text + ' expended' : 'From your watch', subMetric:wkE.on ? 'energyOut' : ''},
    ];
    const hlOpen = S.healthOpen !== false, hlRows = HEALTH.filter(r => HS[r.k]);
    const hl = {
      on:healthOn && st.day <= 0 && hlRows.length > 0, open:hlOpen, chev:hlOpen ? 'expand_less' : 'expand_more',
      summary:hlOpen ? 'From Health Connect' : hlRows.slice(0, 2).map(r => r.label + ' ' + r.value).join(' · '),
      toggle:() => this.update({healthOpen:!hlOpen}),
      rows:hlRows.map(r => ({...r, hasSub:!!r.sub, subMetric:r.subMetric || ''})),
      master:this.toggle(!!healthOn, () => this.update({healthToday:!healthOn})),
      rowToggles:HEALTH.map(r => ({label:r.k === 'workout' ? 'Workouts' : r.label, t:this.toggle(!!HS[r.k], () => this.update({healthRows:{...HS, [r.k]:!HS[r.k]}}))})),
    };
    const navActive = {water:'today', fasting:'more', targets:'more', mealbuilder:'foods', customfood:'foods', modes:'more', reminders:'more', integrations:'more', support:'more', widgets:'more', dayclose:'today', log:'add', weight:'week', nutrients:st.prevScreen === 'week' ? 'week' : 'today'}[st.screen] || st.screen;

    return {
      theme:this.props.theme || 'light', palette:this.props.palette || 'pine', textSize:this.props.textSize || 'normal', scrollRef:this.scrollRef,
      is, go, showNav:st.screen !== 'onb', modeLabel, banner:P.banner || '', hasBanner:!!P.banner && is.today,
      td, meals, rhythm, emptyText:st.day > 0 ? (warm ? 'Plan it now and the app stays quiet later.' : 'Nothing planned.') : (warm ? 'Nothing here yet. Add it whenever.' : 'Nothing logged.'), day,
      safeFoods:['Rice','Banana','Chicken adobo','Toast','Greek yogurt','Apples'], newFoods, newFoodCount:String(newFoods.length),
      copyYesterday:() => this.addItems(YESTERDAY.Dinner.map(x => ({...x})), 'Dinner'),
      slot:st.slot, slotLower,
      slots:this.chips(groupsOn ? ['Breakfast','Lunch','Snack','Dinner','Uncategorized'] : ['Uncategorized'], o => o === st.slot, o => this.setState({slot:o})),
      logTabs:this.seg([{k:'search',label:'Search'},{k:'photo',label:'Photo'},{k:'quick',label:'Describe'},{k:'saved',label:'Saved'}], st.logTab, v => this.setState({logTab:v})),
      lt:{search:st.logTab === 'search', photo:st.logTab === 'photo', quick:st.logTab === 'quick', saved:st.logTab === 'saved'},
      query:st.query, onQuery:e => this.setState({query:e.target.value}), results, noResults:!results.length,
      onScan:() => this.setState({sheet:'add', afReturn:false, foodId:'yogurt', unitIdx:0, qty:1, precision:'Weighed'}),
      photoNote:st.photoNote, onPhotoNote:e => this.setState({photoNote:e.target.value}), photo,
      quickText:st.quickText, onQuickText:e => this.setState({quickText:e.target.value, quickParsed:false}), onParse:() => this.setState({quickParsed:true}),
      quickNum:st.quickNum, onQuickNum:e => this.setState({quickNum:e.target.value.replace(/\D/g, '')}), quick,
      addByName:() => { if (st.quickText.trim()) this.addItems([it(st.quickText.trim(), '', 'Named', null)]); },
      saved, yesterdayLine:(YESTERDAY[st.slot] || []).map(x => x.name.split(',')[0]).join(', '),
      copyYesterdaySlot:() => this.addItems((YESTERDAY[st.slot] || []).map(x => ({...x}))),
      sheet:{add:st.sheet === 'add', quick:st.sheet === 'quick', friction:st.sheet === 'friction' && !!st.friction, cal:st.sheet === 'cal', trends:st.sheet === 'trends', mealMenu:st.sheet === 'mealMenu', mealNote:st.sheet === 'mealNote', mealFeel:st.sheet === 'mealFeel', waterMenu:st.sheet === 'waterMenu', menu:st.sheet === 'addMenu' || st.sheet === 'dayMenu'}, closeSheet:() => this.setState(s => ({sheet:s.afReturn && s.sheet === 'add' ? 'quick' : null, afReturn:false, clearAsk:false})),
      trs, openTrendSettings:() => this.setState({sheet:'trends'}), topBg:st.screen === 'today' ? 'var(--hero)' : 'transparent', topFg:st.screen === 'today' ? '#fff' : 'var(--ink)',
      mealMenu, mmTitle:mm === 'Uncategorized' && !groupsOn ? 'All food' : mm, note, feel,
      lo, groupsOn, hl,
      sw, wt, fs, cal, menuItems:st.sheet === 'addMenu' ? addMenuItems : dayItems, openAddMenu:() => this.setState({sheet:'addMenu', clearAsk:false}), openDayMenu:() => this.setState({sheet:'dayMenu', clearAsk:false}), af, fr, lg, mb, cf, tg, myFoods, noMyFoods:!myFoods.length,
      dc, dayNote:st.dayNote, onDayNote:e => this.setState({dayNote:e.target.value}),
      wk,
      moreRows:[
        {label:'Modes and numbers', sub:modeLabel, onClick:go.modes},
        {label:'Targets', sub:this.vis('calories', S) === 'show' ? `${this.format('calories', baseT, {bare:true})} kcal, ${st.macroMode === 'ratio' ? 'ratio' : 'gram'} macros` : 'Out of view in this mode', onClick:go.targets},
        {label:'Reminders and celebrations', sub:S.quiet ? 'Quiet mode on' : 'Quiet mode off', onClick:go.reminders},
        {label:'Watch and health apps', sub:'Health Connect connected', onClick:go.integrations},
        {label:'Widgets', sub:'Home screen and watch', onClick:go.widgets},
        {label:'Fasting', sub:fastOk ? (S.fasting ? `On, ${S.fastPlan}` : 'Off') : 'Not part of this mode', onClick:go.fasting},
        {label:'Support and sharing', sub:'Helpline, export, delete', onClick:go.support},
      ],
      presets, matrix, goalRange:goalRange.on && !goalRange.soft ? {on:true, text:`${this.format('calories', GOAL.min, {bare:true})} to ${this.format('calories', GOAL.max, {bare:true})} kcal, before activity`} : OFF,
      goalRangeHidden:!(goalRange.on && !goalRange.soft),
      lockToggle:this.toggle(S.lock, () => this.update({lock:!S.lock})),
      changedNote:(S.changedAt ? `Last changed by you, ${S.changedAt}.` : 'Set by you during setup.') + ' App updates never change these.',
      quietToggle:this.toggle(S.quiet, () => this.update({quiet:!S.quiet})), events, mealRem,
      toneOpts:this.seg([{k:'plain',label:'Plain'},{k:'warm',label:'Warm'}], S.tone, v => this.update({tone:v})),
      toneSample:warm ? '"Nothing here yet. Add it whenever."' : '"Nothing logged."',
      tagsToggle:this.toggle(S.askTags, () => this.update({askTags:!S.askTags})),
      wv, weightInput:st.weightInput, onWeightInput:e => this.setState({weightInput:e.target.value, weightSaved:false}),
      onWeightSave:() => { if (st.weightInput) this.setState({weightInput:'', weightSaved:true}); }, weightSaved:st.weightSaved,
      ig, sources, onExcludeDay:() => this.flash('Pick a day on the calendar to leave out.'),
      onExport:() => this.flash('Export ready: friendly-tracking.csv'),
      delAsk:!st.delConfirm, delConfirm:st.delConfirm, onDeleteAsk:() => this.setState({delConfirm:true}), onDeleteCancel:() => this.setState({delConfirm:false}),
      onDelete:() => { this.setState({delConfirm:false}); this.flash('Everything was deleted.'); },
      wg, onb,
      nav:navItems.map(([label, k, icon]) => { const a = navActive === k, add = k === 'add';
        return {label, icon, text:add ? '' : label, onClick:() => (add ? this.openQuick() : this.go(k)),
          bg:add ? 'var(--accent)' : 'transparent', iconFg:add ? 'var(--on-accent)' : a ? 'var(--accent)' : 'var(--muted)', fg:a ? 'var(--ink)' : 'var(--muted)',
          size:add ? '32px' : '25px', box:add ? '60px' : '28px', lift:add ? '-30px' : '0px', shadow:add ? '0 8px 20px rgba(20,20,30,.28)' : 'none',
          fill:a || add ? '1' : '0', weight:a ? '800' : '600'}; }),
      quickActions, recents, mealTiles, nut, tr,
      foodsSearch:() => this.logVia('search'),
      makeRows:[
        {label:'Create a meal', sub:'Group foods you often eat together', icon:'bookmark_add', color:'var(--c-fat)', tint:'var(--t-fat)', onClick:go.mealbuilder},
        {label:'Add a custom food', sub:'For anything we don\'t have', icon:'edit_square', color:'var(--c-carbs)', tint:'var(--t-carbs)', onClick:go.customfood},
      ],
      openNutrients:() => { this.setState({nutRange:'day'}); this.go('nutrients'); },
      openNutrientsWeek:() => { this.setState({nutRange:'week'}); this.go('nutrients'); },
      toast:{on:!!st.toast, text:st.toast || ''},
    };
  }
}
</script>
</body>
</html>
