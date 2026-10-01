const fs=require('fs'),{PHP}=require('@php-wasm/universal'),{loadNodeRuntime}=require('@php-wasm/node'),Parser=require('php-parser');
(async()=>{
const parser=new Parser({parser:{phpVersion:'8.3'}});
for(const f of ['index.php','partners/index.php','lib/portal-r5.php'])parser.parseCode(fs.readFileSync('../web419/'+f,'utf8'));
const lib=fs.readFileSync('../web419/lib/portal-r5.php','utf8').replace(/^<\?php/,'');
const php=new PHP(await loadNodeRuntime('8.3',{emscriptenOptions:{processId:419}}));
const test=`
function fail($s,$c=400){throw new Exception($s,$c);}
function check($v,$s){if(!$v)throw new Exception($s);}
foreach(['08:59'=>false,'09:00'=>true,'17:59'=>true,'18:00'=>false,'23:59'=>false] as $time=>$expected)check(gp_order_window(new DateTimeImmutable('2026-10-01 '.$time,new DateTimeZone('Asia/Tehran')))===$expected,'window '.$time);
$c=['address'=>'old','delivery_addresses'=>[['id'=>'own','address'=>'saved','province'=>'p','city'=>'c']]];
$a=gp_address_snapshot($c,'own');$c['delivery_addresses'][0]['address']='new';check($a['address']==='saved','snapshot');
try{gp_address_snapshot($c,'other');throw new Exception('ownership bypass');}catch(Exception $e){check($e->getCode()===422,'ownership');}
$o=['status'=>'ارسال شد','items'=>[['serials'=>['secret']]],'exit_items'=>[['serials'=>['secret']]]];$hidden=gp_customer_visible_order($o);check(!isset($hidden['items'][0]['serials'])&&!isset($hidden['exit_items'][0]['serials']),'mask');
$o['status']='تحویل شد';check(gp_customer_visible_order($o)['items'][0]['serials']===['secret'],'delivered serial');
echo 'PASS: Tehran order window boundaries, address ownership/snapshot, delivered-only serials';`;
const r=await php.run({code:'<?php $p="";$m="";'+lib+test});console.log(r.text);if(r.errors||!r.text.includes('PASS:'))throw Error(r.errors||r.text);php.exit();})();
