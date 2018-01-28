#include <stdlib.h>

// returns command ID or 0xffffff for invalid
char readBLE(char **buffer){
    Serial2.begin(9600);

    char tempByte = 0x00;
    char opCode = 0xff;
    char parityByte = 0x00;
    int length = 0;

    while(Serial2.available() && tempByte != 0xff00ff) tempByte = Serial2.read();

    if(Serial2.available()){
        opCode = Serial2.read();
    }

    // big-endian
    if(Serial2.available()){
        tempByte = Serial2.read();
        length = (int)tempByte;
        length <<= 8;
        if(Serial2.available()){
            tempByte = Serial2.read();
            length |= (int)tempByte;
        }
        else{
            return 0xff;
        }
    }
    else{
        return 0xff;
    }

    *buffer = malloc(length);
    
    if(readBytes(buffer, length) != length){
        goto cleanup;
    }

    if(Serial2.available()){
        for(int index = 0; index < length; index++){
            parityByte ^= (*buffer)[index];
        }

        tempByte = Serial2.read();
    }
    else {
        goto cleanup;
    }
    
    return opCode;

    cleanup:
    free(*buffer);
    *buffer = NULL;
    return 0xff;
}