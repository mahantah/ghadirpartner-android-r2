<?php
function check($ok,$why){if(!$ok)throw new Exception($why);}
function now(){return '2026-09-23T10:00:00';}
$s=['settings'=>[], 'customers'=>[['id'=>1],['id'=>2]],'special_offers'=>[
 ['id'=>1,'active'=>true,'audience'=>'all','title'=>'عمومی'],
 ['id'=>2,'active'=>true,'audience'=>'selected','customer_ids'=>[2],'title'=>'خصوصی'],
 ['id'=>3,'active'=>true,'audience'=>'all','end_date'=>'2020-01-01'],
 ['id'=>4,'active'=>false,'audience'=>'all'],
 ['id'=>5,'active'=>true,'audience'=>'all','start_date'=>'2099-01-01']
], 'notifications'=>[
 ['id'=>1,'kind'=>'portal_message','audience'=>'selected','customer_ids'=>[1],'title'=>'شخصی','body'=>'متن','created_at'=>now()],
 ['id'=>2,'kind'=>'portal_message','audience'=>'selected','customer_ids'=>[2],'title'=>'محرمانه','body'=>'متن','created_at'=>now()],
 ['id'=>3,'kind'=>'otp','body'=>'123456']
], 'orders'=>[['id'=>1,'customer_id'=>1,'number'=>'GHP-1','status'=>'ارسال شد'],['id'=>2,'customer_id'=>2,'number'=>'GHP-2','status'=>'تحویل شد']]];
$u=['id'=>11,'customer_id'=>1,'roles'=>['customer']];
$rows=gp_inbox_feed($s,$u);check(count($rows)===3,'audience and validity filtering');
check(count(array_filter($rows,fn($r)=>$r['kind']==='offer'))===1,'one offer only');
check(!str_contains(json_encode($rows),'123456'),'no OTP leak');
$id=$rows[0]['id'];$s['settings']['portal_inbox_read']['1'][$id]=now();$rows=gp_inbox_feed($s,$u);check(count(array_filter($rows,fn($r)=>$r['read']))===1,'read receipt');
$u2=['id'=>12,'customer_id'=>2,'roles'=>['customer']];check(!array_filter(gp_inbox_feed($s,$u2),fn($r)=>$r['read']),'receipt isolation');
check(gp_inbox_feed($s,['roles'=>['admin']])===[],'staff must not receive customer feed');
$before=array_values(array_filter($rows,fn($r)=>$r['kind']==='offer'))[0]['id'];$s['special_offers'][0]['title']='تغییر طرح';$after=array_values(array_filter(gp_inbox_feed($s,$u),fn($r)=>$r['kind']==='offer'))[0]['id'];check($before!==$after,'updated offer gets a new read state');
echo "PASS: targeting, expiry, future dates, inactive offers, order ownership, OTP exclusion, per-customer receipts, updated offers\n";
