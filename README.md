# WhatsappBackupReader

## What is WhatsApp Backup Reader?
A very simple api written in java that allows you to read WhatsApp Messenger (https://whatsapp.com) backup files from the android app. Parts of the algorithms have been migrated from https://github.com/ElDavoo/wa-crypt-tools.git.

## Compile
First, clone the repository using git (recommended):
```bash
git clone https://github.com/Terge3141/whatsappbackupreader.git
```

Make sure that *Java 11* and *maven* is installed

Go to the whatsappbackupreader directory and build package
```bash
cd whatsappbackupreader
mvn package
```

## Dump the database and stickers
```java
Path cryptPath = Paths.get("/path/to/msgstore.db.crypt15");
Path keyPath = Paths.get("/path/to/keyfile.txt");
Path outputPath = Paths.get("/path/to/msgstore.db");
Path contactsPath = Paths.get("/path/to/contacts"); // optional

DatabaseDumper dumper = DatabaseDumper.of(cryptPath, keyPath, outputPath);
dumper.setCreateExtraSqlViews(true);

// optional
dumper.readContacts(contactsPath);

dumper.run();
```
* cryptPath: Path to the android whatsapp messenger file, normally has the name msgstore.db.crypt15.
* keyPath: Path to the 64-digit encryption key. See https://faq.whatsapp.com/1246476872801203 for details on how to generate it.
* outputPath: the outputpath where the sql data base is written to, good idea is to name it msgstore.db.
* contactsPath: A semi-colon separated file containing phone number and full name
	* 491511234567;Marty McFly

## Extra views
As the WhatsApp Messenger database structure is quite complicated, additional views can be created. The view v_messages contains all messages with senders and chatnames:
* messageid
* chatname
* sendername
* type\_description
	* possible values: TEXT, PICTURE, AUDIO, VIDEO, CONTACT, STATIC_LOCATION, END-TO-END_ENCRYPTION, DOCUMENT, MISSED_VIDEO_CALL, ANIMATION, DELETED_MESSAGE, LIVE_LOCATION, STICKER, INVITATION_TO_WHATSAPP_GROUP
* text
* timestamp (unix timestamp in milliseconds)

## Run the program to dump database and blobs
```bash
java -cp target/whatsappbackupreader-0.0.1-SNAPSHOT-jar-with-dependencies.jar <cryptpath> <keypath> <outputdir>
```

## Jitpack
The packages can also be obtained from jitpack.io and directly included into gradle or maven. Go to https://jitpack.io/#Terge3141/whatsappbackupreader for further information.
