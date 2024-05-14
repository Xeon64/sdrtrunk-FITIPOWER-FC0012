Added support for FITIPOWER FC0012

If you have a FITIPOWER FC0012 or FC0013 that doesn't work with this driver let me know. Include a bus ID of the device.. and any other info you can spare.

FC0012 has been tested by me and will be updated more at a later date.

Make sure you find your PPM using GQRX tuned to a Control Channel. Allow your SDR tuner to warm up at least 5 minutes before finding your ppm. If you have more than 1 SDR make sure you change the serial numbers with rtl_eeprom first.. and then note the correct PPM for each SDR.  kalibarate-rtl or rtl_test etc.. will NOT work without modification.



Set LNA gain to the highest 19.2db. This SDR tuner will not pickup anything beyond 2.4MHZ sampling although 1.8MHZ seems to work best if your having signal issues because of too much bandwidth.

The key to running massive amounts of SDR tuners is using multiple powered USB hubs spread accross multiple USB controllers / IRQs..  Most high watt USB hubs with more than 10 ports appear to handle about 6-7 tuners before causing USB -9 errors and ppm shifts on the same controller.

When adding powered HUBs don't forget to plug the AC adapter (wall wart) into a surge protecter along with your computer.. it will help save your computer if you have a strong surge from your AC power. I personally run a whole home surge protector that protects the whole house using multiple methods.

If you have a SDR that is not currently supported let me know and I'll try to add it when I have free time.

BabyDodge donations to : 0x6DC22B650C4cc27658115B29325D4bb6e9D5CC66

I'll clean up some more of the code later in future beta releases and possibly add extra decoders [sound,txt,video,tracking,etc.] for various streams.

Thanks !



UPDATE - 05/14/24

I plan on getting back to this project in the next month or so.. I need to update my cheap antennas to a single split antenna.. before I start running the hardware for testing again.. too many wires. I was also thinking about making a custom PCB with a built-in usb hub with all the chips on the board it would make things nice and neat.. and get rid of all the single usb devices in a hub.. and I can skip making custom wires/boosters/filters.. to clean things up and do it right on the PCB board.

In my last test I did notice a bug on systems with more then one GPU.. audio output did not work when there is more than 1 GPU on the system.. no matter which hw port sdrtrunk was set to.. it wouldn't work or list the correct port used by the main audio GPU. I'll work on that issue too when I get a chance. It didn't seem to have anything to do with my code that I updated or any unusual system configs other then having more than one GPU. In other words this bug is most likely on the current DSheirer mainstream branch too.. It will require more testing when I have time.
