
(function(){
  // 侧边栏折叠
  var btn = document.querySelector('.collapse-btn');
  if(btn){
    btn.addEventListener('click', function(){ document.body.classList.toggle('collapsed'); });
  }
  // 菜单分组展开/收起
  Array.prototype.forEach.call(document.querySelectorAll('.menu-group > .menu-item'), function(it){
    it.addEventListener('click', function(){ it.parentElement.classList.toggle('open'); });
  });
  // 页签切换
  Array.prototype.forEach.call(document.querySelectorAll('.tab[data-tab]'), function(t){
    t.addEventListener('click', function(){
      var wrap = t.closest('.tab-wrap');
      if(!wrap) return;
      Array.prototype.forEach.call(wrap.querySelectorAll('.tab'), function(x){ x.classList.remove('active'); });
      t.classList.add('active');
      Array.prototype.forEach.call(wrap.querySelectorAll('.tab-pane'), function(p){ p.classList.remove('show'); });
      var pane = document.getElementById(t.getAttribute('data-tab'));
      if(pane) pane.classList.add('show');
    });
  });
  // 个人中心子菜单切换
  Array.prototype.forEach.call(document.querySelectorAll('.pmenu-item[data-panel]'), function(it){
    it.addEventListener('click', function(){
      var box = it.closest('.profile-card');
      if(!box) return;
      Array.prototype.forEach.call(box.querySelectorAll('.pmenu-item'), function(x){ x.classList.remove('active'); });
      it.classList.add('active');
      Array.prototype.forEach.call(box.querySelectorAll('.ppanel'), function(p){ p.classList.remove('show'); });
      var pane = document.getElementById('panel-' + it.getAttribute('data-panel'));
      if(pane) pane.classList.add('show');
    });
  });
  // ---- 系统管理：弹窗 / 树形权限 / 开关 ----
  var lastModalTrigger = null;
  var lastRow = null; // 最近一次从表格行内打开的弹窗所对应的行（用于编辑预填/保存回写/删除移除）
  // ---- 弹窗打开/关闭/跳转（抽成命名函数，供动态插入的元素补绑） ----
  function onModalTrigger(b, e){
    if(e) e.preventDefault();
    lastModalTrigger = b;
    lastRow = b.closest ? b.closest('tr') : null;
    var m = document.getElementById(b.getAttribute('data-modal'));
    if(!m) return;
    var t = b.getAttribute('data-title');
    if(t){ var h = m.querySelector('.modal-title'); if(h) h.textContent = t; }
    if(m.id === 'modal-msg' && lastRow && lastRow.cells.length){
      var nm = m.querySelector('.m-input');
      if(nm) nm.value = lastRow.cells[0].textContent.trim();
    }
    m.classList.add('show');
    if(m.id === 'modal-exam' && window.__exam){ window.__exam.reset(); window.__exam.start(); }
    if(m.hasAttribute('data-crud')){ if(lastRow) crudPrefill(m, lastRow); else crudClear(m); }
  }
  function onClose(b){
    var m = b.closest('.mask');
    if(m){
      m.classList.remove('show');
      if(m.id === 'modal-exam' && window.__exam) window.__exam.stop();
    }
  }
  function onGoto(b){
    var cur = b.closest('.mask');
    var target = document.getElementById(b.getAttribute('data-goto'));
    if(cur) cur.classList.remove('show');
    if(target){
      target.classList.add('show');
      if(target.id === 'modal-exam' && window.__exam){ window.__exam.reset(); window.__exam.start(); }
    }
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-modal]'), function(b){
    b.__bound = 1;
    b.addEventListener('click', function(e){ onModalTrigger(b, e); });
  });
  Array.prototype.forEach.call(document.querySelectorAll('.mask'), function(m){
    m.addEventListener('click', function(e){
      if(e.target === m){
        m.classList.remove('show');
        if(m.id === 'modal-exam' && window.__exam) window.__exam.stop();
      }
    });
  });
  Array.prototype.forEach.call(document.querySelectorAll('[data-close]'), function(b){
    b.__bound = 1;
    b.addEventListener('click', function(){ onClose(b); });
  });
  // 弹窗内跳转另一个弹窗（如 考试详情 → 开始考试）
  Array.prototype.forEach.call(document.querySelectorAll('[data-goto]'), function(b){
    b.__bound = 1;
    b.addEventListener('click', function(){ onGoto(b); });
  });
  // ---- P2 监考联动：管理员端写入共享状态（localStorage，跨页面演示生效） ----
  function proctorGet(){
    try { return JSON.parse(localStorage.getItem('exam_proctor') || '{}') || {}; } catch(e){ return {}; }
  }
  function proctorState(patch){
    var s = proctorGet();
    for(var k in patch){ if(patch[k] !== undefined) s[k] = patch[k]; }
    try { localStorage.setItem('exam_proctor', JSON.stringify(s)); } catch(e){}
    return s;
  }
  // 事件序号：消息/强制交卷为「事件」，用自增 id 标记，避免依赖时钟（跨页面/跨会话均可靠）
  function proctorNextId(){
    var s = proctorGet();
    s.__n = (s.__n || 0) + 1;
    try { localStorage.setItem('exam_proctor', JSON.stringify(s)); } catch(e){}
    return s.__n;
  }
  window.__proctor = {
    send: function(msg){ proctorState({msg: msg, msgId: proctorNextId()}); },
    pause: function(){ proctorState({paused: true}); },
    resume: function(){ proctorState({paused: false}); },
    force: function(){ proctorState({forceId: proctorNextId()}); }
  };
  function pauseRow(tr, paused){
    if(!tr || !tr.cells.length) return;
    if(tr.cells[2]) tr.cells[2].innerHTML = paused
      ? '<span class="tag tag-orange">已暂停</span>' : '<span class="tag tag-blue">答题中</span>';
    var cell = tr.cells[tr.cells.length - 1];
    if(cell){
      cell.innerHTML = paused
        ? '<a class="link" href="javascript:void(0)" data-after="resume">恢复考试</a> <a class="link" href="javascript:void(0)" data-modal="modal-msg">发送消息</a>'
        : '<a class="link" href="javascript:void(0)" data-modal="cf-pause">暂停考试</a> <a class="link" href="javascript:void(0)" data-modal="cf-force">强制交卷</a> <a class="link" href="javascript:void(0)" data-modal="modal-msg">发送消息</a>';
      if(window.__bindDyn) window.__bindDyn(cell);
    }
  }
  function forceRow(tr){
    if(!tr || !tr.cells.length) return;
    if(tr.cells[2]) tr.cells[2].innerHTML = '<span class="tag tag-green">已交卷</span>';
    var cell = tr.cells[tr.cells.length - 1];
    if(cell){
      cell.innerHTML = '<a class="link" href="javascript:void(0)" data-modal="modal-view-paper">查看试卷</a>';
      if(window.__bindDyn) window.__bindDyn(cell);
    }
  }
  function retakeRow(tr){
    if(!tr || tr.cells.length < 7) return;
    tr.cells[5].innerHTML = '<span class="tag tag-purple">已发回重考</span>';
    tr.cells[6].innerHTML = '<span class="tag tag-gray">未发布</span>';
    var cell = tr.cells[tr.cells.length - 1];
    if(cell){
      cell.innerHTML = '<a class="link" href="javascript:void(0)" data-modal="modal-view-paper">查看试卷</a>';
      if(window.__bindDyn) window.__bindDyn(cell);
    }
  }
  function publishAnswerCfg(){
    var box = document.getElementById('modal-publish');
    if(!box) return;
    var on = '';
    Array.prototype.forEach.call(box.querySelectorAll('.radio-row'), function(r){
      if(r.classList.contains('on')) on = r.textContent || '';
    });
    var hide = on.indexOf('隐藏答案') >= 0;
    var cfg = {};
    try { cfg = JSON.parse(localStorage.getItem('exam_cfg') || '{}') || {}; } catch(e){}
    cfg.showScore = true;
    cfg.showAnswer = !hide;
    try { localStorage.setItem('exam_cfg', JSON.stringify(cfg)); } catch(e){}
  }
  // ---- 确认后联动（报名替换 / 监考暂停恢复 / 强制交卷 / 发回重考 / 发布答案策略） ----
  function onAfter(b){
    var af = b.getAttribute('data-after');
    if(af === 'signup' && lastModalTrigger && lastModalTrigger.parentNode){
      var repl = document.createElement('span');
      repl.className = 'btn btn-gray btn-sm';
      repl.textContent = '已报名';
      lastModalTrigger.parentNode.replaceChild(repl, lastModalTrigger);
    }
    else if(af === 'pause' && lastRow){ proctorState({paused:true}); pauseRow(lastRow, true); }
    else if(af === 'resume'){ proctorState({paused:false}); pauseRow(b.closest('tr'), false); }
    else if(af === 'force' && lastRow){ proctorState({forceId: proctorNextId()}); forceRow(lastRow); }
    else if(af === 'retake' && lastRow){ retakeRow(lastRow); }
    else if(af === 'publish'){ publishAnswerCfg(); }
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-after]'), function(b){
    b.__bound = 1;
    b.addEventListener('click', function(){ onAfter(b); });
  });
  // 动态插入的链接/按钮统一在此补绑事件（暂停/恢复、发回重考等行内联动）
  // ---- 添加大纲：保存即写入表格（演示交互） ----
  var olSave = document.getElementById('ol-save');
  if(olSave){ olSave.addEventListener('click', function(){
    var nm = document.getElementById('ol-name'), lv = document.getElementById('ol-level');
    var name = nm ? nm.value.trim() : '', level = lv ? lv.value : '';
    if(!name){ showToast('请输入大纲名称'); return; }
    var d = new Date(), p = function(n){ return (n < 10 ? '0' + n : n); };
    var ts = d.getFullYear() + '-' + p(d.getMonth()+1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    var tb = document.querySelector('main .table-wrap tbody') || document.querySelector('main table tbody');
    if(tb){
      var tr = document.createElement('tr');
      tr.innerHTML = '<td>' + escHtml(name) + '</td><td>' + escHtml(level) + '</td><td>0</td><td>' + ts + '</td><td>'
        + '<a class="link" href="javascript:void(0)" data-modal="modal-outline" data-title="编辑大纲">编辑</a> <a class="link danger" href="javascript:void(0)" data-modal="cf-del-outline">删除</a></td>';
      tb.insertBefore(tr, tb.firstChild);
      if(window.__bindDyn) window.__bindDyn(tr);
    }
    showToast('大纲「' + name + '」已添加');
  }); }

  window.__bindDyn = function(root){
    root = root || document;
    Array.prototype.forEach.call(root.querySelectorAll('[data-modal]'), function(b){
      if(b.__bound) return; b.__bound = 1;
      b.addEventListener('click', function(e){ onModalTrigger(b, e); });
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-close]'), function(b){
      if(b.__bound) return; b.__bound = 1;
      b.addEventListener('click', function(){ onClose(b); });
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-goto]'), function(b){
      if(b.__bound) return; b.__bound = 1;
      b.addEventListener('click', function(){ onGoto(b); });
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-after]'), function(b){
      if(b.__bound) return; b.__bound = 1;
      b.addEventListener('click', function(){ onAfter(b); });
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-toast]'), function(b){
      if(b.__bound) return; b.__bound = 1;
      b.addEventListener('click', function(e){ e.preventDefault(); showToast(b.getAttribute('data-toast')); });
    });
  };
  window.__bindDyn(document);
  Array.prototype.forEach.call(document.querySelectorAll('.tree-parent > .tree-label .tree-cb'), function(cb){
    cb.addEventListener('change', function(){
      var box = cb.closest('.tree-node');
      Array.prototype.forEach.call(box.querySelectorAll('.tree-children .tree-cb'), function(c){ c.checked = cb.checked; });
    });
  });
  Array.prototype.forEach.call(document.querySelectorAll('.tree-tw'), function(t){
    t.addEventListener('click', function(e){ e.stopPropagation(); t.closest('.tree-node').classList.toggle('closed'); });
  });
  Array.prototype.forEach.call(document.querySelectorAll('.switch'), function(s){
    s.addEventListener('click', function(){ s.classList.toggle('off'); });
  });
  // ---- 通用 toast 反馈 ----
  var toastTimer = null;
  function showToast(msg){
    var t = document.getElementById('toast');
    if(!t){ t = document.createElement('div'); t.id = 'toast'; t.className = 'toast'; document.body.appendChild(t); }
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function(){ t.classList.remove('show'); }, 1800);
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-toast]'), function(b){
    b.__bound = 1;
    b.addEventListener('click', function(e){
      e.preventDefault();
      showToast(b.getAttribute('data-toast'));
    });
  });
  // ---- 选项/策略详情行：上移下移删除（事件委托，支持动态新增） ----
  function moveRow(btn, dir){
    var row = btn.closest('.opt-row, .sd-row');
    if(!row) return;
    if(dir < 0 && row.previousElementSibling) row.parentNode.insertBefore(row, row.previousElementSibling);
    if(dir > 0 && row.nextElementSibling) row.parentNode.insertBefore(row.nextElementSibling, row);
  }
  Array.prototype.forEach.call(document.querySelectorAll('.opt-list, .sd-list'), function(box){
    box.addEventListener('click', function(e){
      var t = e.target;
      if(t.closest('.row-up')) moveRow(t.closest('.row-up'), -1);
      else if(t.closest('.row-down')) moveRow(t.closest('.row-down'), 1);
      else if(t.closest('.row-del')){ var r = t.closest('.opt-row, .sd-row'); if(r) r.remove(); }
    });
  });
  var optAdd = document.getElementById('opt-add');
  if(optAdd){
    optAdd.addEventListener('click', function(){
      var list = document.getElementById('opt-list');
      var rows = list.querySelectorAll('.opt-row');
      if(!rows.length) return;
      var clone = rows[rows.length - 1].cloneNode(true);
      var idx = clone.querySelector('.opt-idx');
      if(idx) idx.textContent = String.fromCharCode(65 + rows.length);
      var inp = clone.querySelector('.m-input'); if(inp) inp.value = '';
      var sw = clone.querySelector('.switch'); if(sw) sw.classList.add('off');
      list.appendChild(clone);
    });
  }
  var sdAdd = document.getElementById('sd-add');
  if(sdAdd){
    sdAdd.addEventListener('click', function(){
      var list = document.getElementById('sd-list');
      var rows = list.querySelectorAll('.sd-row');
      if(!rows.length) return;
      var clone = rows[rows.length - 1].cloneNode(true);
      Array.prototype.forEach.call(clone.querySelectorAll('.m-input'), function(i){ i.value = ''; });
      list.appendChild(clone);
    });
  }
  // ---- 题型分段切换（试题编辑） ----
  Array.prototype.forEach.call(document.querySelectorAll('.seg'), function(seg){
    Array.prototype.forEach.call(seg.querySelectorAll('.seg-item'), function(it){
      it.addEventListener('click', function(){
        Array.prototype.forEach.call(seg.querySelectorAll('.seg-item'), function(x){ x.classList.remove('on'); });
        it.classList.add('on');
        var t = it.getAttribute('data-qtype');
        var box = seg.closest('.modal-body') || document;
        Array.prototype.forEach.call(box.querySelectorAll('.qsec'), function(s){
          s.style.display = (s.getAttribute('data-qtypes') || '').indexOf(t) >= 0 ? '' : 'none';
        });
      });
    });
  });
  // ---- 弹窗内页签 ----
  Array.prototype.forEach.call(document.querySelectorAll('.mtab'), function(t){
    t.addEventListener('click', function(){
      var box = t.closest('.modal-body');
      Array.prototype.forEach.call(box.querySelectorAll('.mtab'), function(x){ x.classList.remove('on'); });
      t.classList.add('on');
      Array.prototype.forEach.call(box.querySelectorAll('.mtab-body'), function(b){ b.classList.remove('on'); });
      var target = document.getElementById(t.getAttribute('data-tab'));
      if(target) target.classList.add('on');
    });
  });
  // ---- 单选组（组卷方式 / 发布模式） ----
  Array.prototype.forEach.call(document.querySelectorAll('.radio-row'), function(r){
    r.addEventListener('click', function(){
      var g = r.closest('.radio-group');
      if(g){ Array.prototype.forEach.call(g.querySelectorAll('.radio-row'), function(x){ x.classList.remove('on'); }); }
      r.classList.add('on');
      var show = r.getAttribute('data-show');
      if(show){
        Array.prototype.forEach.call(document.querySelectorAll('.paper-mode'), function(m){
          m.style.display = m.getAttribute('data-mode') === show ? '' : 'none';
        });
      }
    });
  });
  // ---- 下拉菜单（更多导出） ----
  Array.prototype.forEach.call(document.querySelectorAll('.dd-toggle'), function(t){
    t.addEventListener('click', function(e){
      e.stopPropagation();
      var menu = t.parentNode.querySelector('.dd-menu');
      Array.prototype.forEach.call(document.querySelectorAll('.dd-menu.show'), function(m){ if(m !== menu) m.classList.remove('show'); });
      if(menu) menu.classList.toggle('show');
    });
  });
  document.addEventListener('click', function(){
    Array.prototype.forEach.call(document.querySelectorAll('.dd-menu.show'), function(m){ m.classList.remove('show'); });
  });
  // ---- 学员端 · 答题界面（P2：乱序/最短时长/考试次数/到点交卷/答案联动/监考联动） ----
  var examBox = document.getElementById('modal-exam');
  if(examBox){
    var examStandalone = examBox.closest('.mask') === null;
    var examState = {cur:1, total:4, answers:{}};
    var examLeft = 30 * 60, examTimerInt = null;
    var examElapsed = 0, examMin = 1, examUsed = 0, examTimes = 1;
    var examTrigger = null, examEndAt = '', examShuffleOn = false, examOrderBak = null;
    var lastMsgId = 0, lastForceId = 0, wasPaused = false, proctorInt = null;
    var qboxes = examBox.querySelectorAll('.q-box');
    function examCfg(){
      try { return JSON.parse(localStorage.getItem('exam_cfg') || '{}') || {}; } catch(e){ return {}; }
    }
    function fmt(s){ var m = Math.floor(s / 60), x = s % 60; return (m < 10 ? '0' + m : m) + ':' + (x < 10 ? '0' + x : x); }
          function paintTimer(){
        var el = document.getElementById('exam-timer');
        if(!el) return;
        var hh = Math.floor(examLeft / 3600), mm = Math.floor((examLeft % 3600) / 60), ss = examLeft % 60;
        el.textContent = (hh < 10 ? '0' + hh : hh) + '时' + (mm < 10 ? '0' + mm : mm) + '分' + (ss < 10 ? '0' + ss : ss) + '秒';
        el.classList.toggle('urgent', examLeft <= 300);
        var ee = document.getElementById('exam-elapsed');
        if(ee) ee.textContent = '已答 ' + fmt(examElapsed);
        var sb = document.getElementById('exam-submit');
        if(sb){
          var left = examMin * 60 - examElapsed;
          if(left > 0){ sb.textContent = '立即交卷（' + fmt(left) + '）'; sb.title = '最短答题时长未满，暂不能交卷'; }
          else { sb.textContent = '立即交卷'; sb.title = ''; }
        }
      }
          function qShow(n){
        examState.cur = n;
        Array.prototype.forEach.call(qboxes, function(b){ b.classList.toggle('on', b.getAttribute('data-q') == String(n)); });
        Array.prototype.forEach.call(examBox.querySelectorAll('.sheet-cell'), function(c){
          c.classList.toggle('cur', c.getAttribute('data-jump') == String(n));
        });
        var box = examBox.querySelector('.q-box[data-q="' + n + '"]');
        if(box) box.scrollIntoView({behavior:'smooth', block:'start'});
      }
    function qMark(){
      Array.prototype.forEach.call(examBox.querySelectorAll('.sheet-cell'), function(c){
        var n = c.getAttribute('data-jump');
        var a = examState.answers[n];
        c.classList.toggle('done', a !== undefined && a !== '');
      });
    }
    // ---- 乱序（防作弊）：题目 + 选项 + 答题卡同步 ----
    function shuffleArr(a){ for(var i = a.length - 1; i > 0; i--){ var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
    function backupOrder(){
      if(examOrderBak) return;
      var main = examBox.querySelector('.exam-main');
      var grid = examBox.querySelector('.sheet-grid');
      examOrderBak = {main: Array.prototype.slice.call(main.children), grid: Array.prototype.slice.call(grid.children)};
    }
    function renumber(){
      var main = examBox.querySelector('.exam-main');
      Array.prototype.forEach.call(main.children, function(box, i){
        var t = box.querySelector('.q-title');
        if(!t) return;
        var full = t.getAttribute('data-full') || t.textContent;
        t.setAttribute('data-full', full);
        t.textContent = (i + 1) + '、' + full.replace(/^\d+、/, '');
      });
    }
    function shuffleExam(manual){
      var cfg = examCfg();
      if(!manual && !examShuffleOn && !cfg.shuffle) return;
      backupOrder();
      var main = examBox.querySelector('.exam-main');
      var boxes = Array.prototype.slice.call(main.querySelectorAll('.q-box'));
      shuffleArr(boxes).forEach(function(b){ main.appendChild(b); });
      Array.prototype.forEach.call(main.querySelectorAll('.q-box'), function(b){
        var opts = Array.prototype.slice.call(b.querySelectorAll('.q-opt'));
        if(opts.length > 1) shuffleArr(opts).forEach(function(o){ b.appendChild(o); });
      });
      var grid = examBox.querySelector('.sheet-grid');
      var cells = Array.prototype.slice.call(grid.querySelectorAll('.sheet-cell'));
      shuffleArr(cells).forEach(function(c){ grid.appendChild(c); });
      renumber();
      qShow(examState.cur);
      qMark();
      showToast('试题与选项顺序已重新打乱（防作弊）');
    }
    function restoreOrder(){
      if(!examOrderBak) return;
      var main = examBox.querySelector('.exam-main');
      examOrderBak.main.forEach(function(b){ main.appendChild(b); });
      var grid = examBox.querySelector('.sheet-grid');
      examOrderBak.grid.forEach(function(c){ grid.appendChild(c); });
      Array.prototype.forEach.call(main.querySelectorAll('.q-box'), function(b){
        var t = b.querySelector('.q-title');
        if(t && t.getAttribute('data-full')) t.textContent = t.getAttribute('data-full');
      });
      examOrderBak = null;
      qShow(examState.cur);
      qMark();
    }
    // ---- 答题交互 ----
    Array.prototype.forEach.call(examBox.querySelectorAll('.q-opt'), function(opt){
      opt.addEventListener('click', function(){
        var box = opt.closest('.q-box');
        var n = box.getAttribute('data-q');
        var multi = box.getAttribute('data-type') === 'multi';
        if(multi){
          opt.classList.toggle('on');
          var sel = [];
          Array.prototype.forEach.call(box.querySelectorAll('.q-opt.on'), function(o){ sel.push(o.getAttribute('data-k')); });
          examState.answers[n] = sel.join('');
        } else {
          Array.prototype.forEach.call(box.querySelectorAll('.q-opt'), function(o){ o.classList.remove('on'); });
          opt.classList.add('on');
          examState.answers[n] = opt.getAttribute('data-k');
        }
        qMark();
      });
    });
    Array.prototype.forEach.call(examBox.querySelectorAll('.q-textarea'), function(ta){
      ta.addEventListener('input', function(){
        var n = ta.closest('.q-box').getAttribute('data-q');
        examState.answers[n] = ta.value.trim();
        qMark();
      });
    });
      Array.prototype.forEach.call(examBox.querySelectorAll('.sheet-cell'), function(c){
        c.addEventListener('click', function(){ qShow(Number(c.getAttribute('data-jump'))); });
      });
      Array.prototype.forEach.call(examBox.querySelectorAll('.q-flag'), function(f){
        f.addEventListener('click', function(e){
          e.stopPropagation();
          var n = f.getAttribute('data-flag');
          var box = examBox.querySelector('.q-box[data-q="' + n + '"]');
          if(box) box.classList.toggle('q-marked');
          var cell = examBox.querySelector('.sheet-cell[data-jump="' + n + '"]');
          if(cell) cell.classList.toggle('mark');
          f.textContent = (box && box.classList.contains('q-marked')) ? '取消标记' : '标记一下';
        });
      });
      var qPrev = document.getElementById('q-prev'), qNext = document.getElementById('q-next');
      if(qPrev) qPrev.addEventListener('click', function(){ if(examState.cur > 1) qShow(examState.cur - 1); });
      if(qNext) qNext.addEventListener('click', function(){ if(examState.cur < examState.total) qShow(examState.cur + 1); });
      var fsMinus = document.getElementById('fs-minus'), fsPlus = document.getElementById('fs-plus');
      var examMain = examBox.querySelector('.exam-main');
      var examFs = 15;
      function examSetFs(){ if(examMain) examMain.style.fontSize = examFs + 'px'; }
      if(fsMinus) fsMinus.addEventListener('click', function(){ if(examFs > 12){ examFs--; examSetFs(); } });
      if(fsPlus) fsPlus.addEventListener('click', function(){ if(examFs < 24){ examFs++; examSetFs(); } });
      var totop = document.getElementById('exam-totop');
      if(totop) totop.addEventListener('click', function(){ var m = examBox.querySelector('.exam-main'); if(m) m.scrollTo({top:0, behavior:'smooth'}); });
    var shufBtn = document.getElementById('exam-shuffle');
    if(shufBtn) shufBtn.addEventListener('click', function(){ shuffleExam(true); });
    var endSim = document.getElementById('exam-end-sim');
    if(endSim) endSim.addEventListener('click', function(){
      autoSubmit('已到计划结束时间（' + (examEndAt || '考试计划结束') + '），系统已自动交卷');
    });
    // ---- 计分 + 成绩/答案可见性联动 ----
    function gradeAndShow(reason){
      var correct = {'1':'B', '2':'ABC', '3':'A', '4':''};
      var right = 0;
      for(var i = 1; i <= examState.total; i++){
        var a = examState.answers[i] || '';
        if(i === 4){ if(a) right++; }
        else if(a === correct[String(i)]) right++;
      }
      var score = Math.round(right / examState.total * 100);
      var cfg = examCfg();
      var showScore = cfg.showScore !== false;
      var showAnswer = cfg.showAnswer !== false;
      var box = document.getElementById('modal-result');
      if(box){ box.setAttribute('data-show-score', showScore ? '1' : '0'); box.setAttribute('data-show-answer', showAnswer ? '1' : '0'); }
      var sb = document.getElementById('result-score-box'), sh = document.getElementById('result-score-hidden');
      if(sb) sb.style.display = showScore ? '' : 'none';
      if(sh) sh.style.display = showScore ? 'none' : '';
      var ah = document.getElementById('result-answer-hidden');
      if(ah) ah.style.display = showAnswer ? 'none' : '';
      var rl = document.getElementById('review-list');
      if(rl){ rl.classList.remove('on'); rl.style.display = showAnswer ? '' : 'none'; }
      var rn = document.getElementById('result-num');
      if(rn) rn.innerHTML = score + '<small> 分</small>';
      var rc = document.getElementById('result-correct');
      if(rc) rc.textContent = right + ' / ' + examState.total;
      var rs = document.getElementById('result-status');
      if(rs){
        rs.textContent = score >= 60 ? '及格' : '不及格';
        rs.style.color = score >= 60 ? '#18a058' : '#f5483b';
      }
      var rb = document.getElementById('result-review-btn');
      if(rb){ rb.textContent = '查看答案'; rb.style.display = showAnswer ? '' : 'none'; }
      if(reason) showToast(reason);
      document.getElementById('modal-result').classList.add('show');
      useAttempt();
    }
    // ---- 考试次数拦截：每次交卷消耗一次 ----
    function useAttempt(){
      if(!examTrigger) return;
      var card = examTrigger.closest ? examTrigger.closest('.exam-card') : null;
      if(!card) return;
      var used = examUsed + 1;
      var meta = card.querySelector('.stu-meta');
      if(meta){
        var txt = meta.textContent;
        meta.textContent = /已考 \d+ 次/.test(txt) ? txt.replace(/已考 \d+ 次/, '已考 ' + used + ' 次') : (txt + ' · 已考 ' + used + ' 次');
      }
      if(used >= examTimes && examTrigger.parentNode){
        var sp = document.createElement('span');
        sp.className = 'btn btn-gray btn-sm';
        sp.style.cursor = 'not-allowed';
        sp.title = '考试次数已用完';
        sp.textContent = '次数已用完';
        examTrigger.parentNode.replaceChild(sp, examTrigger);
      }
    }
    function autoSubmit(reason){
      if(examTimerInt){ clearInterval(examTimerInt); examTimerInt = null; }
      var cf = document.getElementById('cf-submit');
      if(cf) cf.classList.remove('show');
      examBox.classList.remove('show');
      gradeAndShow(reason);
    }
    // ---- 交卷前确认（最短答题时长拦截） ----
    var submitBtn = document.getElementById('exam-submit');
    if(submitBtn) submitBtn.addEventListener('click', function(){
      if(examElapsed < examMin * 60){
        showToast('答题时间不足最短时长 ' + examMin + ' 分钟，暂不能交卷');
        return;
      }
      var answered = 0;
      for(var k in examState.answers){ if(examState.answers[k]) answered++; }
      var left = examState.total - answered;
      var txt = document.getElementById('cf-submit-text');
      if(txt) txt.innerHTML = left > 0
        ? ('还有 <b style="color:#f5483b">' + left + '</b> 道题未作答，确认交卷吗？')
        : '所有题目均已作答，确认交卷吗？';
      document.getElementById('cf-submit').classList.add('show');
    });
    var cfOk = document.getElementById('exam-submit-ok');
    if(cfOk) cfOk.addEventListener('click', function(){
      document.getElementById('cf-submit').classList.remove('show');
      examBox.classList.remove('show');
      gradeAndShow('');
    });
    // ---- 查看答案 ----
    var rv = document.getElementById('result-review-btn');
    if(rv) rv.addEventListener('click', function(){
      var rl = document.getElementById('review-list');
      if(!rl) return;
      rl.classList.toggle('on');
      rv.textContent = rl.classList.contains('on') ? '收起答案' : '查看答案';
    });
    // ---- 监考联动：消息横幅 / 暂停阻断 / 强制交卷 ----
    var bc = document.getElementById('exam-banner-close');
    if(bc) bc.addEventListener('click', function(){
      var b = document.getElementById('exam-banner');
      if(b) b.classList.remove('show');
    });
    function pollProctor(){
      var s = {};
      try { s = JSON.parse(localStorage.getItem('exam_proctor') || '{}') || {}; } catch(e){}
      if(s.msg && s.msgId && s.msgId > lastMsgId){
        lastMsgId = s.msgId;
        var t = document.getElementById('exam-banner-text');
        if(t) t.textContent = s.msg;
        var b = document.getElementById('exam-banner');
        if(b) b.classList.add('show');
      }
      if(s.forceId && s.forceId > lastForceId){
        lastForceId = s.forceId;
        autoSubmit('监考老师已强制交卷');
        return;
      }
      var paused = !!s.paused;
      var p = document.getElementById('exam-paused');
      if(paused && !wasPaused){
        wasPaused = true;
        if(p) p.classList.add('show');
        if(examTimerInt){ clearInterval(examTimerInt); examTimerInt = null; }
      } else if(!paused && wasPaused){
        wasPaused = false;
        if(p) p.classList.remove('show');
        startTicker();
        showToast('考试已恢复，倒计时继续');
      }
    }
    function startTicker(){
      if(examTimerInt) return;
      examTimerInt = setInterval(function(){
        if(examLeft <= 0){
          clearInterval(examTimerInt); examTimerInt = null;
          autoSubmit('考试时间到，系统已自动交卷');
          return;
        }
        examLeft--; examElapsed++; paintTimer();
      }, 1000);
    }
    // ---- 对外状态机 ----
    window.__exam = {
      reset: function(){
        var trig = (typeof lastModalTrigger !== 'undefined') ? lastModalTrigger : null;
        examTrigger = trig;
        examTimes = trig && trig.getAttribute('data-times') ? Number(trig.getAttribute('data-times')) : 1;
        examUsed = trig && trig.getAttribute('data-used') ? Number(trig.getAttribute('data-used')) : 0;
        var cfgNow = examCfg();
        examMin = cfgNow.min ? Number(cfgNow.min)
          : (trig && trig.getAttribute('data-min') ? Number(trig.getAttribute('data-min')) : 1);
        examEndAt = trig && trig.getAttribute('data-end') ? trig.getAttribute('data-end') : '';
        var nm = trig && trig.getAttribute('data-name') ? trig.getAttribute('data-name') : '';
        var nameEl = examBox.querySelector('.exam-name');
        if(nameEl && nm) nameEl.textContent = nm;
        examShuffleOn = !!(trig && trig.getAttribute('data-shuffle')) || !!cfgNow.shuffle;
        var flag = document.getElementById('exam-shuffle-flag');
        if(flag) flag.style.display = examShuffleOn ? '' : 'none';
        var mt = document.getElementById('exam-min-tip');
        if(mt) mt.textContent = '最短 ' + examMin + ' 分钟';
        var examDur = (trig && trig.getAttribute('data-dur')) || examBox.getAttribute('data-dur'); examLeft = (examDur ? Number(examDur) : 30) * 60; examElapsed = 0;
        examState.answers = {}; examState.cur = 1;
        restoreOrder();
        Array.prototype.forEach.call(examBox.querySelectorAll('.q-opt'), function(o){ o.classList.remove('on'); });
        Array.prototype.forEach.call(examBox.querySelectorAll('.q-textarea'), function(t2){ t2.value = ''; });
        var bb = document.getElementById('exam-banner'); if(bb) bb.classList.remove('show');
        var pp = document.getElementById('exam-paused'); if(pp) pp.classList.remove('show');
        wasPaused = false;
        var ps = {};
        try { ps = JSON.parse(localStorage.getItem('exam_proctor') || '{}') || {}; } catch(e){}
        lastMsgId = ps.msgId || 0;
        lastForceId = ps.forceId || 0;
        qShow(1); qMark(); paintTimer();
        var stEl = document.getElementById('exam-start-time');
        if(stEl){ var d = new Date(); var p2 = function(n){ return (n < 10 ? '0' + n : n); }; stEl.textContent = p2(d.getHours()) + ':' + p2(d.getMinutes()) + ':' + p2(d.getSeconds()); }
        if(examShuffleOn) shuffleExam(false);
      },
      start: function(){
        startTicker();
        if(proctorInt) clearInterval(proctorInt);
        proctorInt = setInterval(pollProctor, 1500);
        pollProctor();
      },
      stop: function(){
        if(examTimerInt){ clearInterval(examTimerInt); examTimerInt = null; }
        if(proctorInt){ clearInterval(proctorInt); proctorInt = null; }
      },
      __setElapsed: function(s){ examElapsed = s; paintTimer(); }
    };
    qShow(1);
    // 防作弊：切屏检测
    document.addEventListener('visibilitychange', function(){
      if(document.hidden && (examBox.classList.contains('show') || examStandalone)){
        showToast('⚠ 检测到切屏行为，系统已记录（防作弊）');
      }
    });
    // 独立页面（非弹窗）自动开始考试
    if(examStandalone){
      lastModalTrigger = examBox;
      window.__exam.reset();
      window.__exam.start();
    }
  }
  // ---- P2-2 监考联动：发送消息写入共享状态 ----
  var msgSend = document.getElementById('msg-send');
  if(msgSend) msgSend.addEventListener('click', function(){
    var ta = document.querySelector('#modal-msg textarea');
    proctorState({msg: ta ? ta.value.trim() : '', msgId: proctorNextId()});
  });
  // ---- P2-4 多人阅卷：双评分取平均分 / 隐藏考生信息 ----
  var markBox = document.getElementById('modal-mark');
  if(markBox){
    var ma = document.getElementById('mark-a'), mb = document.getElementById('mark-b');
    var avgBox = document.getElementById('mark-avg-box'), avgVal = document.getElementById('mark-avg');
    var nameEl = document.getElementById('mark-name');
    var realName = nameEl ? (nameEl.getAttribute('data-real') || nameEl.textContent) : '';
    function calcAvg(){
      var a = ma ? parseFloat(ma.value) : NaN, b = mb ? parseFloat(mb.value) : NaN;
      if(!isNaN(a) && !isNaN(b) && avgBox && avgVal){
        avgVal.textContent = Math.round((a + b) * 10) / 10;
        avgBox.classList.add('show');
      } else if(avgBox){ avgBox.classList.remove('show'); }
    }
    if(ma) ma.addEventListener('input', calcAvg);
    if(mb) mb.addEventListener('input', calcAvg);
    var hideSw = document.getElementById('mark-hide');
    function paintName(){
      if(!nameEl) return;
      var hidden = hideSw ? !hideSw.classList.contains('off') : false;
      nameEl.textContent = hidden ? realName.charAt(0) + '**' : realName;
    }
    if(hideSw) hideSw.addEventListener('click', paintName);
    paintName();
    var msub = document.getElementById('mark-submit');
    if(msub) msub.addEventListener('click', function(){
      var a = ma ? parseFloat(ma.value) : NaN;
      if(isNaN(a)){ showToast('请输入评分'); return; }
      var b = mb ? parseFloat(mb.value) : NaN;
      showToast(isNaN(b)
        ? ('评分提交成功（' + a + ' 分）')
        : ('评分提交成功，平均分 ' + (Math.round((a + b) * 10) / 10) + ' 分'));
      markBox.classList.remove('show');
    });
  }
  // ---- P2-1 考试计划配置本地持久化（乱序/显示成绩/显示答案/最短时长） ----
  (function(){
    var KEY = 'exam_cfg';
    function getCfg(){
      try { return JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch(e){ return {}; }
    }
    function setCfg(patch){
      var c = getCfg();
      for(var k in patch){ if(patch[k] !== undefined) c[k] = patch[k]; }
      try { localStorage.setItem(KEY, JSON.stringify(c)); } catch(e){}
    }
    function bindSwitch(id, key){
      var sw = document.getElementById(id);
      if(!sw) return;
      var c = getCfg();
      // 仅当配置里有显式取值时才覆盖 HTML 默认态（默认态由 switch_row 的 on 参数决定）
      if(c[key] !== undefined){
        if(c[key]) sw.classList.remove('off'); else sw.classList.add('off');
      }
      sw.addEventListener('click', function(){
        var v = {};
        v[key] = !sw.classList.contains('off');
        setCfg(v);
      });
    }
    bindSwitch('cfg-shuffle', 'shuffle');
    bindSwitch('cfg-show-score', 'showScore');
    bindSwitch('cfg-show-answer', 'showAnswer');
    var mn = document.getElementById('cfg-min');
    if(mn){
      var c = getCfg();
      if(c.min) mn.value = c.min;
      mn.addEventListener('input', function(){ setCfg({min: mn.value}); });
    }
  })();
  // 筛选重置
  Array.prototype.forEach.call(document.querySelectorAll('[data-reset]'), function(b){
    b.addEventListener('click', function(){
      var bar = b.closest('.filter-bar');
      if(!bar) return;
      Array.prototype.forEach.call(bar.querySelectorAll('input'), function(i){ i.value=''; });
      Array.prototype.forEach.call(bar.querySelectorAll('select'), function(s){ s.selectedIndex=0; });
      if(bar.__apply) bar.__apply();
    });
  });
  // 表格全选
  var all = document.querySelector('.check-all');
  if(all){
    all.addEventListener('change', function(){
      Array.prototype.forEach.call(document.querySelectorAll('tbody .row-check'), function(c){ c.checked = all.checked; });
    });
  }

  // ================================================================ P0/P1 增强：真实交互层
  function escHtml(s){ return String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
  function normLabel(s){ return String(s || '').replace(/[:：*①②③④⑤⑥⑦⑧⑨\s]/g, ''); }
  // 弹窗字段名 -> 表头列名 别名
  var COL_ALIAS = {
    '考试试卷': '试卷名称', '考试开始时间': '开始时间', '考试结束时间': '结束时间',
    '考试时长（分钟）': '考试时长', '最短考试时长（分钟）': '最短考试时长',
    '排序顺序': '排序', '试题难度': '难度', '满分分数': '满分', '教师': '教师姓名',
    '考生姓名': '考生', '报名人': '姓名'
  };
  function colOf(tbl, label, used){
    if(!tbl || !label) return -1;
    label = normLabel(label);
    var ths = tbl.tHead ? tbl.tHead.rows[0].cells : [];
    var i, h;
    var alias = COL_ALIAS[label] || label;
    for(i = 0; i < ths.length; i++){
      h = normLabel(ths[i].textContent);
      if(h && h === alias && used.indexOf(i) < 0) return i;
    }
    var best = -1, bestLen = 99;
    for(i = 0; i < ths.length; i++){
      h = normLabel(ths[i].textContent);
      if(!h || used.indexOf(i) >= 0) continue;
      if(h.indexOf(label) >= 0 || label.indexOf(h) >= 0){
        if(h.length < bestLen){ best = i; bestLen = h.length; }
      }
    }
    return best;
  }
  function mainTable(){
    var m = document.querySelector('main');
    if(!m) return null;
    var tbls = m.querySelectorAll('table');
    for(var i = 0; i < tbls.length; i++){ if(!tbls[i].closest('.mask')) return tbls[i]; }
    return null;
  }
  function pageTableOf(mask){
    var box = mask.parentNode;
    if(!box) return null;
    var tbls = box.querySelectorAll('table');
    for(var i = 0; i < tbls.length; i++){ if(!tbls[i].closest('.mask')) return tbls[i]; }
    return null;
  }
  // ---- CRUD 弹窗字段收集（label -> 列索引）----
  function crudFields(mask){
    var tbl = pageTableOf(mask);
    var out = [];
    if(!tbl) return out;
    var used = [];
    var items = mask.querySelectorAll('.m-item');
    for(var i = 0; i < items.length; i++){
      var lab = items[i].querySelector('.m-label');
      var ctrl = items[i].querySelector('input.m-input, select.m-select, textarea.m-input');
      if(!lab || !ctrl) continue;
      var col = colOf(tbl, lab.textContent, used);
      if(col < 0) continue;
      out.push({col: col, type: ctrl.tagName === 'SELECT' ? 'select' : 'text', ctrl: ctrl});
      used.push(col);
    }
    var sws = mask.querySelectorAll('.switch-row');
    for(var i = 0; i < sws.length; i++){
      var lab2 = sws[i].querySelector('.m-label');
      var sw2 = sws[i].querySelector('.switch');
      if(!lab2 || !sw2) continue;
      var col2 = colOf(tbl, lab2.textContent, used);
      if(col2 < 0) continue;
      out.push({col: col2, type: 'switch', ctrl: sw2});
      used.push(col2);
    }
    return out;
  }
  function crudClear(mask){
    Array.prototype.forEach.call(mask.querySelectorAll('.m-item input, .m-item select, .m-item textarea'), function(c){
      if(c.tagName === 'SELECT') c.selectedIndex = 0; else c.value = '';
    });
    Array.prototype.forEach.call(mask.querySelectorAll('.switch-row .switch'), function(s){ s.classList.remove('off'); });
  }
  function crudPrefill(mask, tr){
    if(!tr || !tr.cells) return;
    var fs = crudFields(mask);
    for(var i = 0; i < fs.length; i++){
      var f = fs[i];
      var cell = tr.cells[f.col];
      var val = cell ? cell.textContent.trim() : '';
      if(f.type === 'switch'){
        f.ctrl.classList.toggle('off', !/启用|公开|正常|是|显示/.test(val));
      } else if(f.type === 'select'){
        for(var j = 0; j < f.ctrl.options.length; j++){
          if(f.ctrl.options[j].text === val){ f.ctrl.selectedIndex = j; break; }
        }
      } else {
        f.ctrl.value = val;
      }
    }
  }
  function fieldVal(f){
    if(f.type === 'switch') return f.ctrl.classList.contains('off') ? '停用' : '启用';
    if(f.type === 'select') return f.ctrl.options[f.ctrl.selectedIndex].text;
    return f.ctrl.value;
  }
  function tagClassOf(v){
    if(/启用|正常|公开|已发布|进行中|已阅卷|是/.test(v)) return 'tag-green';
    if(/不公开|报名|待阅卷|未开始/.test(v)) return 'tag-orange';
    return 'tag-gray';
  }
  function setCell(cell, v, keepTag){
    if(!cell) return;
    var tg = cell.querySelector('.tag');
    if(tg){
      var cls = keepTag === false ? tagClassOf(v) : ((tg.className.match(/tag-\w+/) || ['tag-gray'])[0]);
      cell.innerHTML = '<span class="tag ' + cls + '">' + escHtml(v) + '</span>';
      return;
    }
    var tn = cell.querySelector('.tnode');
    if(tn && tn.childNodes.length){ tn.childNodes[tn.childNodes.length - 1].textContent = v; return; }
    cell.textContent = v;
  }
  function crudSave(mask){
    var tbl = pageTableOf(mask);
    if(!tbl) return false;
    var fs = crudFields(mask);
    if(lastRow && lastRow.cells){
      for(var i = 0; i < fs.length; i++) setCell(lastRow.cells[fs[i].col], fieldVal(fs[i]), true);
      return true;
    }
    var rows = tbl.tBodies[0].rows;
    if(rows.length){
      var clone = rows[rows.length - 1].cloneNode(true);
      clone.removeAttribute('data-filtered');
      clone.style.display = '';
      var cks = clone.querySelectorAll('input');
      for(var k = 0; k < cks.length; k++) cks[k].checked = false;
      for(var i = 0; i < fs.length; i++) setCell(clone.cells[fs[i].col], fieldVal(fs[i]), false);
      tbl.tBodies[0].appendChild(clone);
      return true;
    }
    return false;
  }
  // ---- 筛选栏：真实过滤 ----
  function initFilterBar(bar){
    var box = bar.parentNode;
    var tbl = null, cards = null;
    var tbls = box.querySelectorAll('table');
    for(var i = 0; i < tbls.length; i++){ if(!tbls[i].closest('.mask')){ tbl = tbls[i]; break; } }
    if(!tbl){
      var cs = box.querySelectorAll('.exam-card, .signup-item');
      if(cs.length) cards = cs;
    }
    if(!tbl && !cards) return;
    bar.__apply = function(){
      var specs = [];
      var ctrls = bar.querySelectorAll('.f-input, .f-select');
      for(var j = 0; j < ctrls.length; j++){
        var c = ctrls[j];
        var p = c.previousElementSibling;
        while(p && !(p.classList && p.classList.contains('f-label'))) p = p.previousElementSibling;
        specs.push({c: c, col: tbl && p ? colOf(tbl, p.textContent, []) : -1});
      }
      var n = 0;
      var items = tbl ? tbl.tBodies[0].rows : cards;
      for(var i = 0; i < items.length; i++){
        var el = items[i], ok = true;
        for(var j2 = 0; j2 < specs.length; j2++){
          var sp = specs[j2], v = (sp.c.value || '').trim();
          if(!v) continue;
          if(sp.c.tagName === 'SELECT' && sp.c.selectedIndex <= 0) continue;
          var text;
          if(tbl && sp.col >= 0 && el.cells[sp.col]) text = el.cells[sp.col].textContent;
          else text = el.textContent;
          if(text.indexOf(v) < 0){ ok = false; break; }
        }
        if(el.dataset) el.dataset.filtered = ok ? '0' : '1';
        el.style.display = ok ? '' : 'none';
        if(ok) n++;
      }
      if(tbl){
        var info = box.querySelector('.pg-info');
        if(info) info.textContent = '共 ' + n + ' 条';
      }
      refreshPagers();
    };
    var q = null, rs = null;
    var bts = bar.querySelectorAll('.f-actions button');
    for(var i = 0; i < bts.length; i++){
      if(!q && bts[i].textContent.indexOf('查询') >= 0){ q = bts[i]; }
      if(!rs && bts[i].textContent.indexOf('重置') >= 0){ rs = bts[i]; }
    }
    if(q) q.addEventListener('click', function(){ bar.__apply(); });
    if(rs) rs.addEventListener('click', function(){
      Array.prototype.forEach.call(bar.querySelectorAll('input'), function(inp){ inp.value = ''; });
      Array.prototype.forEach.call(bar.querySelectorAll('select'), function(sel){ sel.selectedIndex = 0; });
      bar.__apply();
    });
  }
  // ---- 分页：真实翻页 ----
  function refreshPagers(){
    var pgs = document.querySelectorAll('.pager, .pager2');
    Array.prototype.forEach.call(pgs, function(pg){ initPager(pg); });
  }
  function initPager(pg){
    var wrap = pg.closest('.table-wrap') || pg.parentNode;
    var tbl = wrap.querySelector('table');
    if(!tbl || !tbl.tBodies[0]) return;
    var sizeSel = pg.querySelector('.pg-size');
    var size = sizeSel ? (parseInt(sizeSel.value, 10) || 10) : 5;
    var rows = tbl.tBodies[0].rows;
    var total = 0;
    for(var i = 0; i < rows.length; i++){ if(rows[i].dataset.filtered !== '1') total++; }
    var pages = Math.max(1, Math.ceil(total / size));
    if(!pg.__page || pg.__page > pages) pg.__page = 1;
    var page = pg.__page;
    var shown = 0;
    for(var i = 0; i < rows.length; i++){
      var vis = rows[i].dataset.filtered !== '1';
      var inPage = vis && shown >= (page - 1) * size && shown < page * size;
      if(vis) shown++;
      rows[i].style.display = inPage ? '' : 'none';
    }
    var info2 = pg.querySelector('.pg-info2');
    var html = '';
    if(info2){
      html += '<span class="pg-info2">第 ' + (total ? (page - 1) * size + 1 : 0) + '-' + Math.min(page * size, total) + ' 条/总共 ' + total + ' 条</span>';
    } else {
      html += '<span class="pg-info">共 ' + total + ' 条</span>';
    }
    html += '<button class="pg" data-pg="prev"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>';
    var start = Math.max(1, Math.min(page - 2, pages - 4));
    var end = Math.min(pages, start + 4);
    start = Math.max(1, end - 4);
    for(var p = start; p <= end; p++){
      html += '<button class="pg' + (p === page ? ' active' : '') + '">' + p + '</button>';
    }
    if(end < pages) html += '<button class="pg" disabled>···</button><button class="pg">' + pages + '</button>';
    html += '<button class="pg" data-pg="next"' + (page >= pages ? ' disabled' : '') + '>下一页</button>';
    if(sizeSel) html += sizeSel.outerHTML;
    pg.innerHTML = html;
    if(!pg.__bound){
      pg.__bound = true;
      pg.addEventListener('click', function(e){
        var t = e.target.closest ? e.target.closest('button.pg') : null;
        if(!t || t.disabled) return;
        var txt = t.textContent;
        if(t.getAttribute('data-pg') === 'prev') pg.__page = Math.max(1, (pg.__page || 1) - 1);
        else if(t.getAttribute('data-pg') === 'next') pg.__page = (pg.__page || 1) + 1;
        else if(/^\d+$/.test(txt)) pg.__page = parseInt(txt, 10);
        else return;
        initPager(pg);
      });
      pg.addEventListener('change', function(e){
        if(e.target.classList.contains('pg-size')){ pg.__page = 1; initPager(pg); }
      });
    }
  }
  // ---- 批量操作 / 确认框行行为 / CRUD 保存 ----
  function checkedRows(tbl){
    var out = [];
    if(!tbl) return out;
    var cks = tbl.querySelectorAll('tbody .row-check');
    for(var i = 0; i < cks.length; i++){
      if(cks[i].checked){
        var tr = cks[i].closest('tr');
        if(tr) out.push(tr);
      }
    }
    return out;
  }
  function statusCell(tr, re){
    for(var i = 0; i < tr.cells.length; i++){
      if(re.test(tr.cells[i].textContent)) return tr.cells[i];
    }
    return null;
  }
  function setTag(cell, text, kind){
    if(!cell) return;
    cell.innerHTML = '<span class="tag tag-' + kind + '">' + escHtml(text) + '</span>';
  }
  document.addEventListener('click', function(e){
    var t = e.target;
    if(!t.closest) return;
    var ra = t.closest('[data-rowact]');
    if(ra){
      var tr = ra.closest('tr');
      var ract = ra.getAttribute('data-rowact');
      if(tr && ract === 'publish'){ setTag(statusCell(tr, /已发布|未发布/), '已发布', 'green'); showToast('成绩已发布'); }
      else if(tr && ract === 'unpublish'){ setTag(statusCell(tr, /已发布|未发布/), '未发布', 'gray'); showToast('已取消发布'); }
      return;
    }
    var bb = t.closest('[data-batch]');
    if(bb){
      var mask = bb.closest('.mask');
      var tbl = (mask && pageTableOf(mask)) || mainTable();
      var act = bb.getAttribute('data-batch');
      var rows = checkedRows(tbl);
      if(act === 'publish'){
        if(!rows.length){ showToast('请先勾选需要发布成绩的记录'); return; }
        for(var i = 0; i < rows.length; i++) setTag(statusCell(rows[i], /已发布|未发布/), '已发布', 'green');
        showToast('成绩已发布');
      } else if(act === 'unpublish-all'){
        var list = rows.length ? rows : Array.prototype.slice.call(tbl ? tbl.tBodies[0].rows : []);
        for(var i = 0; i < list.length; i++) setTag(statusCell(list[i], /已发布|未发布/), '未发布', 'gray');
        showToast('已批量取消发布');
      } else if(act === 'calc'){
        if(!rows.length){ showToast('请先勾选需要计算成绩的记录'); return; }
        var n = 0;
        for(var i = 0; i < rows.length; i++){
          var c = statusCell(rows[i], /待阅卷|已阅卷/);
          if(c && c.textContent.indexOf('待阅卷') >= 0){ setTag(c, '已阅卷', 'blue'); n++; }
        }
        showToast(n ? ('成绩计算完成，' + n + ' 条记录已更新') : '成绩计算完成');
      }
      refreshPagers();
      return;
    }
    var ok = t.closest('.mask .modal-foot .btn-primary');
    if(!ok) return;
    var m2 = ok.closest('.mask');
    if(m2.hasAttribute('data-crud')){
      crudSave(m2);
      m2.classList.remove('show');
      showToast(ok.getAttribute('data-toast') || '保存成功');
      refreshPagers();
      return;
    }
    var id = m2.id;
    if(id.indexOf('cf-del') === 0){
      if(lastRow){ lastRow.remove(); lastRow = null; }
      refreshPagers();
    } else if(id === 'cf-unpublish' && lastRow){
      setTag(statusCell(lastRow, /已发布|未发布/), '未发布', 'gray');
    }
  });
  // ---- 导出 CSV ----
  function exportCSV(tbl, name){
    var head = tbl.tHead ? tbl.tHead.rows[0].cells : [];
    var cols = [];
    for(var i = 0; i < head.length; i++){ if((head[i].textContent || '').trim()) cols.push(i); }
    var lines = [cols.map(function(i){ return head[i].textContent.trim(); }).join(',')];
    var rows = tbl.tBodies[0].rows;
    for(var r = 0; r < rows.length; r++){
      if(rows[r].dataset.filtered === '1') continue;
      var cells = [];
      for(var j = 0; j < cols.length; j++){
        var td = rows[r].cells[cols[j]];
        cells.push('"' + (td ? td.textContent.trim().replace(/"/g, '""') : '') + '"');
      }
      lines.push(cells.join(','));
    }
    var blob = new Blob(['\ufeff' + lines.join('\r\n')], {type: 'text/csv;charset=utf-8'});
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast('已导出 ' + name + '.csv');
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-export]'), function(b){
    b.addEventListener('click', function(){
      var kind = b.getAttribute('data-export');
      var tbl = mainTable();
      if(!tbl){ showToast('演示版：当前页面暂无数据可导出'); return; }
      var name = kind === 'detail' ? '成绩明细' : (kind === 'paper' ? '考生试卷' : (kind === 'report' ? '成绩分析报告' : (kind === 'stats' ? '成绩统计' : '交卷记录')));
      exportCSV(tbl, name + '_20261021');
    });
  });
  // ---- 顶栏：消息铃铛 / 用户菜单 ----
  Array.prototype.forEach.call(document.querySelectorAll('.tb-item > button'), function(b){
    b.addEventListener('click', function(e){
      e.stopPropagation();
      var menu = b.parentNode.querySelector('.tb-menu');
      Array.prototype.forEach.call(document.querySelectorAll('.tb-menu.show'), function(m){ if(m !== menu) m.classList.remove('show'); });
      if(menu) menu.classList.toggle('show');
    });
  });
  document.addEventListener('click', function(){
    Array.prototype.forEach.call(document.querySelectorAll('.tb-menu.show'), function(m){ m.classList.remove('show'); });
  });
  Array.prototype.forEach.call(document.querySelectorAll('.tb-msg'), function(m){
    m.addEventListener('click', function(){ m.classList.remove('unread'); });
  });
  var allRead = document.querySelector('.tb-allread');
  if(allRead) allRead.addEventListener('click', function(e){
    e.stopPropagation();
    Array.prototype.forEach.call(document.querySelectorAll('.tb-msg'), function(m){ m.classList.remove('unread'); });
    var dot = document.querySelector('.topbar-right .dot-red');
    if(dot) dot.style.display = 'none';
    showToast('已全部标记为已读');
  });
  // ---- 上传文件：显示已选文件名 ----
  Array.prototype.forEach.call(document.querySelectorAll('.upload-box input[type="file"]'), function(inp){
    inp.addEventListener('change', function(){
      var box = inp.closest('.upload-box');
      if(inp.files && inp.files[0] && box){
        var nm = box.querySelector('.upload-name');
        if(nm) nm.textContent = '已选择：' + inp.files[0].name;
        box.classList.add('has-file');
        showToast('已选择文件：' + inp.files[0].name);
      }
    });
  });
  // ---- 报名中心：搜索过滤 ----
  Array.prototype.forEach.call(document.querySelectorAll('.search-box'), function(sb){
    var pane = sb.closest('.tab-pane') || sb.parentNode;
    var btn = sb.parentNode.querySelector('.btn-primary');
    var list = pane.querySelector('.signup-list');
    if(!btn || !list) return;
    btn.addEventListener('click', function(){
      var inp = sb.querySelector('input');
      var v = (inp && inp.value || '').trim();
      var n = 0;
      Array.prototype.forEach.call(list.querySelectorAll('.signup-item'), function(it){
        var ok = !v || it.textContent.indexOf(v) >= 0;
        it.style.display = ok ? '' : 'none';
        if(ok) n++;
      });
      showToast(v ? ('共找到 ' + n + ' 条相关报名') : '请输入关键词后再查询');
    });
  });
  // ---- 练习弹窗：刷题 / 模拟考试 ----
  var quizBox = document.getElementById('modal-quiz');
  if(quizBox){
    var qbAll = [], quizCorrect = {}, quizState = {cur:1, total:0, answers:{}};
    function quizActiveSet(){ return quizBox.querySelector('.quiz-set.on') || quizBox.querySelector('.quiz-set'); }
    function quizInit(){
      var act = quizActiveSet(); if(!act) return;
      qbAll = Array.prototype.slice.call(act.querySelectorAll('.q-box'));
      quizCorrect = {};
      Array.prototype.forEach.call(qbAll, function(b){ quizCorrect[b.getAttribute('data-q')] = b.getAttribute('data-correct') || ''; });
      quizState = {cur:1, total:qbAll.length, answers:{}};
      Array.prototype.forEach.call(quizBox.querySelectorAll('.q-opt'), function(o){ o.classList.remove('on','right','wrong'); });
      Array.prototype.forEach.call(quizBox.querySelectorAll('.q-box'), function(b){ b.classList.remove('done'); });
      var res = document.getElementById('quiz-res'); if(res) res.style.display = 'none';
      var qs = document.getElementById('quiz-submit'); if(qs) qs.style.display = '';
      var qa = document.getElementById('quiz-again'); if(qa) qa.style.display = 'none';
      var nm = document.getElementById('quiz-set-name'); if(nm) nm.textContent = act.getAttribute('data-name') || '';
      var bi = document.getElementById('quiz-bar-info'); if(bi) bi.textContent = '题库：' + (act.getAttribute('data-name') || '');
      quizShow(1);
    }
    function quizShow(n){
      quizState.cur = n;
      var act = quizActiveSet();
      var scope = act ? act : quizBox;
      Array.prototype.forEach.call(scope.querySelectorAll('.q-box'), function(b){
        b.classList.toggle('on', b.getAttribute('data-q') == String(n));
      });
      var pg = document.getElementById('quiz-prog'); if(pg) pg.textContent = '第 ' + n + ' / ' + quizState.total + ' 题';
      var pg2 = document.getElementById('quiz-prog2'); if(pg2) pg2.textContent = '第 ' + n + ' / ' + quizState.total + ' 题';
      var prev = document.getElementById('q-prev'), next = document.getElementById('q-next');
      if(prev) prev.disabled = n <= 1;
      if(next) next.disabled = n >= quizState.total;
    }
    Array.prototype.forEach.call(quizBox.querySelectorAll('.q-opt'), function(opt){
      opt.addEventListener('click', function(){
        var box = opt.closest('.q-box');
        var n = box.getAttribute('data-q');
        var multi = box.getAttribute('data-type') === 'multi';
        if(multi){
          opt.classList.toggle('on');
          var sel = [];
          Array.prototype.forEach.call(box.querySelectorAll('.q-opt.on'), function(o){ sel.push(o.getAttribute('data-k')); });
          quizState.answers[n] = sel.join('');
        } else {
          Array.prototype.forEach.call(box.querySelectorAll('.q-opt'), function(o){ o.classList.remove('on'); });
          opt.classList.add('on');
          quizState.answers[n] = opt.getAttribute('data-k');
        }
      });
    });
    var qp = document.getElementById('quiz-prev'), qn = document.getElementById('quiz-next');
    if(qp) qp.addEventListener('click', function(){ if(quizState.cur > 1) quizShow(quizState.cur - 1); });
    if(qn) qn.addEventListener('click', function(){ if(quizState.cur < quizState.total) quizShow(quizState.cur + 1); });
    var qs = document.getElementById('quiz-submit');
    if(qs) qs.addEventListener('click', function(){
      var right = 0, done = 0;
      Array.prototype.forEach.call(qbAll, function(b){
        var n = b.getAttribute('data-q');
        var a = quizState.answers[n] || '';
        b.classList.add('done');
        Array.prototype.forEach.call(b.querySelectorAll('.q-opt'), function(o){
          var k = o.getAttribute('data-k');
          o.classList.remove('on');
          if(a && a.indexOf(k) >= 0) o.classList.add('on');
          if(k === quizCorrect[n]) o.classList.add('right');
          else if(a.indexOf(k) >= 0) o.classList.add('wrong');
        });
        if(a){ done++; if(a === quizCorrect[n]) right++; }
      });
      var res = document.getElementById('quiz-res');
      if(res){
        res.style.display = 'block';
        var sn = document.getElementById('quiz-res-num'); if(sn) sn.textContent = Math.round(right / quizState.total * 100);
        var sc = document.getElementById('quiz-res-correct'); if(sc) sc.textContent = right + ' / ' + quizState.total;
      }
      var again = document.getElementById('quiz-again'); if(again) again.style.display = '';
      if(qs) qs.style.display = 'none';
      showToast(done < quizState.total ? ('还有 ' + (quizState.total - done) + ' 题未作答，已提交') : '练习完成');
    });
    var qa = document.getElementById('quiz-again');
    if(qa) qa.addEventListener('click', function(){ quizInit(); });
    Array.prototype.forEach.call(quizBox.querySelectorAll('.quiz-tab'), function(tab){
      tab.addEventListener('click', function(e){
        e.preventDefault();
        var key = tab.getAttribute('data-set');
        Array.prototype.forEach.call(quizBox.querySelectorAll('.quiz-set'), function(st){ st.classList.toggle('on', st.getAttribute('data-set') === key); });
        Array.prototype.forEach.call(quizBox.querySelectorAll('.quiz-tab'), function(t){ t.classList.toggle('on', t === tab); });
        quizInit();
      });
    });
    quizInit();
  }// ---- 初始化 ----
  Array.prototype.forEach.call(document.querySelectorAll('.filter-bar'), initFilterBar);
  refreshPagers();
})();
