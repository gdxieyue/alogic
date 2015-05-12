package com.anysoft.xscript;

import org.w3c.dom.Element;

import com.anysoft.util.BaseException;
import com.anysoft.util.Properties;
import com.anysoft.util.XmlElementProperties;

/**
 * Log语句
 * @author duanyy
 *
 */
public class Log extends AbstractStatement {
	protected String pattern;
	
	public Log(String xmlTag,Statement _parent) {
		super(xmlTag,_parent);
	}

	public void configure(Element _e, Properties _properties)
			throws BaseException {
		XmlElementProperties p = new XmlElementProperties(_e,_properties);
		pattern = p.GetValue("msg", "", false, true);
	}
	
	public int onExecute(Properties p,ExecuteWatcher watcher) {
		logger.info(p.transform(pattern));
		return 0;
	}	
}