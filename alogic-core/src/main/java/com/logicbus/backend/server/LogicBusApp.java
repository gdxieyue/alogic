package com.logicbus.backend.server;

import javax.servlet.ServletContext;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import com.anysoft.metrics.core.Fragment;
import com.anysoft.metrics.core.MetricsHandler;
import com.anysoft.stream.Handler;
import com.anysoft.util.DefaultProperties;
import com.anysoft.util.IOTools;
import com.anysoft.util.PropertiesConstants;
import com.anysoft.util.Settings;
import com.anysoft.util.XmlTools;
import com.anysoft.util.resource.ResourceFactory;
import com.anysoft.webloader.WebApp;
import com.logicbus.backend.AccessController;
import com.logicbus.backend.IpAndServiceAccessController;
import com.logicbus.backend.QueuedServantFactory;
import com.logicbus.backend.ServantFactory;
import com.logicbus.backend.bizlog.BizLogger;


/**
 * anyLogicBus基于anyWebLoader的应用
 * 
 * @author duanyy
 * @version 1.2.4.5 [20140709 duanyy]
 * - 增加扩展的配置文件
 * 
 * @version 1.2.5 [20140723 duanyy]
 * - 修正ResourceFactory的bug
 * 
 * @version 1.2.6 [20140807 duanyy] <br>
 * - ServantPool和ServantFactory插件化
 * 
 * @version 1.2.6.5 [20140828 duanyy] <br>
 * - 增加onInit/onDestroy事件，以便子类更好的进行初始化和销毁. <br>
 * 
 * @version 1.2.7 [20140828 duanyy] <br>
 * - 重写BizLogger
 * 
 * @version 1.2.8 [20140914 duanyy] <br>
 * - 增加指标收集体系
 * 
 * @version 1.2.8.1 [20140919 duanyy] <br>
 * - MetricsHandler:getInstance拆分为getClientInstance和getServerInstance
 * 
 * @version 1.3.0.2 [20141031 duanyy] <br>
 * - 增加全局配置文件，变量名为settings.global.master和settings.global.secondary
 * 
 * @version 1.6.3.37 [20140806 duanyy] <br>
 * - 淘汰旧的timer框架，采用新的timer框架 <br>
 */
public class LogicBusApp implements WebApp {
	/**
	 * a logger of log4j
	 */
	protected static Logger logger = LogManager.getLogger(LogicBusApp.class);
		
	protected void onInit(Settings settings){	
		ClassLoader classLoader = Settings.getClassLoader();
		
		XmlTools.setDefaultEncoding(settings.GetValue("http.encoding","utf-8"));
		
		//初始化AccessController
		{
			String acClass = settings.GetValue("acm.module", 
					"com.logicbus.backend.IpAndServiceAccessController");
			
			logger.info("AccessController is initializing,module:" + acClass);
			AccessController ac = null;
			try {
				AccessController.TheFactory acf = new AccessController.TheFactory(classLoader);
				ac = acf.newInstance(acClass,settings);
			}catch (Throwable t){
				ac = new IpAndServiceAccessController(settings);
				logger.error("Failed to initialize AccessController.Using default:" + IpAndServiceAccessController.class.getName());
			}
			settings.registerObject("accessController", ac);
		}

		//初始化BizLogger
		{
			String bizLogHome = PropertiesConstants.getString(settings, "bizlog.home", "");
			if (bizLogHome == null || bizLogHome.length() <= 0){
				logger.info("bizlog.home is not set.Set it to /var/log/bizlog");
				settings.SetValue("bizlog.home","var/log/bizlog");
			}
			BizLogger bizLogger = BizLogger.TheFactory.getLogger(settings);
			if (bizLogger != null){
				logger.info("BizLogger is initialized,module:" + bizLogger.getClass().getName());
				settings.registerObject("bizLogger", bizLogger);
			}else{
				logger.error("Can not create a bizlogger instance..");
			}
		}
		
		//初始化MetricsHandler
		// since 1.2.8
		{
			Handler<Fragment> handler = MetricsHandler.TheFactory.getClientInstance(settings);
			if (handler != null){
				logger.info("MetricsHandler is initalized,module:" + handler.getClass().getName());
				settings.registerObject("metricsHandler", handler);
			}else{
				logger.error("Can not create a metrics handler instance.");
			}
		}

		//初始化servantFactory
		{
			String sfClass = PropertiesConstants.getString(settings, "servant.factory", "com.logicbus.backend.QueuedServantFactory");
			logger.info("Servant Factory is initializing,module:" + sfClass);
			ServantFactory sf = null;
			try {
				ServantFactory.TheFactory sfFactory = new ServantFactory.TheFactory();
				sf = sfFactory.newInstance(sfClass, settings);
			}catch (Throwable t){
				sf = new QueuedServantFactory(settings);
				logger.error("Failed to initialize servantFactory.Using default:" + QueuedServantFactory.class.getName());
			}
			settings.registerObject("servantFactory", sf);
		}
	}
	
	
	public void init(DefaultProperties props,ServletContext sc) {
		Settings settings = Settings.get();
		settings.addSettings(props);
		
		// 初始化一些object		
		ClassLoader classLoader = (ClassLoader) sc.getAttribute("classLoader");
		if (classLoader == null){
			classLoader = LogicBusApp.class.getClassLoader();
		}
		settings.registerObject("classLoader", classLoader);
		
		//resourceFactory
		String rf = settings.GetValue("resource.factory","com.anysoft.util.resource.ResourceFactory");
		ResourceFactory resourceFactory = null;
		try {
			logger.info("Use resource factory:" + rf);
			resourceFactory = (ResourceFactory) classLoader.loadClass(rf).newInstance();
		} catch (Exception e) {
			logger.error("Can not create instance of :" + rf);
		}
		if (resourceFactory == null){
			resourceFactory = new ResourceFactory();
			logger.info("Use default:" + ResourceFactory.class.getName());
		}
		settings.registerObject("ResourceFactory", resourceFactory);
		
		//装入全局配置文件
		String globalProfile = settings.GetValue("settings.global.master", "");
		String globalSecondaryProfile = settings.GetValue("settings.global.secondary","");
		if (globalProfile != null && globalProfile.length() > 0){
			logger.info("Load global xml settings");
			logger.info("Url = " + globalProfile);
			settings.addSettings(globalProfile, globalSecondaryProfile, resourceFactory);
			logger.info("Load global xml settings..OK!");
		}
		
		//装入扩展的配置文件
		String extProfile = settings.GetValue("settings.ext.master", "");
		String extSecondaryProfile = settings.GetValue("settings.ext.secondary", "");
		if (extProfile != null && extProfile.length() > 0){
			logger.info("Load ext xml settings");
			logger.info("Url = " + extProfile);
			settings.addSettings(extProfile,extSecondaryProfile,resourceFactory);
			logger.info("Load xml settings..OK!");
		}
		
		// 装入配置文件
		String profile = settings.GetValue("settings.master",
				"java:///com/logicbus/backend/server/profile.default.xml#com.logicbus.backend.server.LogicBusApp");	
		String secondary_profile = settings.GetValue("settings.secondary",
				"java:///com/logicbus/backend/server/profile.default.xml#com.logicbus.backend.server.LogicBusApp");
		
		logger.info("Load xml settings..");
		logger.info("Url = " + profile);
		settings.addSettings(profile,secondary_profile,resourceFactory);
		logger.info("Load xml settings..OK!");

		onInit(settings);
	}

	protected void onDestroy(Settings settings){		
		ServantFactory sf = (ServantFactory)settings.get("servantFactory");
		if (sf != null){
			logger.info("The servantFactory is closing..");
			IOTools.close(sf);
		}

		BizLogger bizLogger = (BizLogger)settings.get("bizLogger");
		if (bizLogger != null){
			logger.info("The bizLogger is closing..");
			IOTools.close(bizLogger);
		}
		
		// since 1.2.8
		MetricsHandler metricsHandler = (MetricsHandler)settings.get("metricsHandler");
		if (metricsHandler != null){
			logger.info("The metrics handler is closing..");
			IOTools.close(metricsHandler);
		}
	}
	
	public void destroy(ServletContext sc) {
		onDestroy(Settings.get());
	}
}
