package com.distrace.examples;

/**
 *
 */
public class InfiniteLoop {
    public static void main(String[] args){
        try{
            randomSleep();}
        catch (Exception ignored){
        }
    }

    public static void randomSleep() throws InterruptedException{
        while(true){
            // randomly sleeps between 500ms and 1200s
            long randomSleepDuration = (long) (500 + Math.random() * 700);
            System.out.printf("Sleeping for %d ms ..\n", randomSleepDuration);
            Thread.sleep(randomSleepDuration);
        }
    }
}
