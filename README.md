Added support for FITIPOWER FC0012

If you have a FITIPOWER FC0012 or FC0013 that doesn't work with this driver let me know. Include a bus ID of the device.. and any other info you can spare.

FC0012 has been tested by me and will be updated more at later date.

Make sure you find your PPM using GQRX tuned to a Control Channel. If you have more than 1 SDR make sure you change the serial numbers with rtl_eeprom first.. and then note the correct PPM for each SDR.  kalibarate-rtl or rtl_test etc.. will NOT work without modification.

Also make sure you disable auto cal ppm in sdrtrunk. Set LNA gain to the highest 19.2db. This SDR tuner will not pickup anything beyond 2.4MHZ sampling although 1.8MHZ seems to work best if your having signal issues because of too much bandwidth.

If you have a SDR that is not currently supported let me know and I'll try to add it when I have free time.

BabyDodge donations to : 0x6DC22B650C4cc27658115B29325D4bb6e9D5CC66

I'll clean up some more of the code later in future beta releases and possibly add extra decoders [sound,txt,video,tracking,etc.] for various streams.

Thanks !
