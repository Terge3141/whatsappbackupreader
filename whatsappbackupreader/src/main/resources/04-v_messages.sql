/*
Replaces the chatid and senderid from v_messages_with_ids by names
*/
create view v_messages as

select m.messageid, cn.name 'chatname', ac.name 'username', m.text, m.timestamp from v_messages_with_ids m
left join v_chatnames cn on m.chatid = cn.chatid
left join v_allcontacts ac on m.senderjid = ac.userid