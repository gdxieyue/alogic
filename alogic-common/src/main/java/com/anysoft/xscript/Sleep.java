package com.anysoft.xscript;

import org.w3c.dom.Element;

import com.anysoft.util.Properties;
import com.anysoft.util.XmlElementProperties;

/**
 * Sleep语句
 * 
 * @author duanyy
 *
 */
public class Sleep extends AbstractStatement {
	protected String pattern;
	public static final String STMT_ASYNC = "async";
	
	public Sleep(String xmlTag,Statement _parent) {
		super(xmlTag,_parent);
	}
	
	protected int compiling(Element _e, Properties _properties,CompileWatcher watcher){
		XmlElementProperties p = new XmlElementProperties(_e,_properties);
		pattern = p.GetValue("timeout", "0", false, true);
		return 0;
	}

	public int onExecute(Properties p,ExecuteWatcher watcher) {
		long timeout = 0;
		try {
			timeout = Long.parseLong(p.transform(pattern));
		}catch (Exception ex){
			logger.error("Can not get sleep time:" + pattern);
		}
		if (timeout > 0){
			try {
				Thread.sleep(timeout);
			} catch (InterruptedException e) {
				
			}
		}
		return 0;
	}

}
