#include "readBLE.h"
#include "typeOfBill.h"

#define READINGS    3
#define COLORS      3

void setup() {
    // put your setup code here, to run once:
    Serial.begin(9600);
    
    int databaseSize = 3;

    int reading[READINGS][COLORS];
    int database[databaseSize][READINGS][COLORS];

    for(int i = 0; i < READINGS; i++){
        for(int j = 0; j < COLORS; j++){
            reading[i][j] = random(200);
            Serial.println("reading " + i + " " + j + " " + reading[i][j]);
        }
    }

    Serial.println();

    for(int h = 0; h < databaseSize; h++){
        for(int i = 0; i < READINGS; i++){
            for(int j = 0; j < COLORS; j++){
                database[h][i][j] = random(200);
                Serial.println("database " + h + " " + i + " " + j + " " + database[h][i][j]);
            }
        }
    }

    Serial.println();

    Serial.println("Type of bill: " + typeOfBill(COLORS, READINGS, databaseSize, reading, database));

    char* buffer;
    readBLE(&buffer);
}

void loop() {
    // put your main code here, to run repeatedly:

}