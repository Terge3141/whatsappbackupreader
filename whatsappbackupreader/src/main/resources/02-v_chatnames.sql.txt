/*
Contains the name for every chat._id. In case of a one-on-one conversation the contact name of the other particpants is used as a chat name.
*/
create view v_chatnames as

/* chats groups */
select _id 'chatid', subject 'name' from chat
where subject NOT NULL

union

/* one-on-one */
select c._id, COALESCE(ac.name, j.user) from chat c
left join jid j on c.jid_row_id = j._id
left join v_allcontacts ac on j._id = ac.userid
where subject IS NULL

union

/* myself */
SELECT -1, "name" from v_allcontacts where "user" = "-1"
