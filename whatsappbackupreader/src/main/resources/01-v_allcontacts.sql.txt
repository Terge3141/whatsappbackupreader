/*
Contains all contact names for jid.user. If the contact name for a specific number is unknown the number is listed instead
*/
CREATE VIEW v_allcontacts AS

SELECT DISTINCT(j._id) 'userid', COALESCE(kc.name, j.user) 'name' FROM jid j
LEFT JOIN notorig_knowncontacts kc ON j.user = kc.user

UNION

SELECT user, name FROM notorig_knowncontacts WHERE user = "-1"
