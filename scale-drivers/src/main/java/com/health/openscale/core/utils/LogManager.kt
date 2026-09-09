package com.health.openscale.core.utils
/** Deliberately suppress protocol payloads and personal measurements in production logs. */
object LogManager {
 fun d(tag:String,message:String){}; fun i(tag:String,message:String){}
 fun w(tag:String,message:String,t:Throwable?=null){};fun e(tag:String,message:String,t:Throwable?=null){}
}
