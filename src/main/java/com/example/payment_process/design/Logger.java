package com.example.payment_process.design;

public class Logger {
    private static volatile Logger instance=null;
    private Logger(){}

    public static Logger getInstance(){
        if(instance==null) {
            synchronized (Logger.class) {
                if(instance==null) {
                    instance=new Logger();
                }
            }
        }
        return instance;
    }

    public void info(String message, String key) {
        System.out.println("[LOG]=====>" + message+ "[LOG]=====>"+key);
    }
}
