package com.seibel.lod.fabric.wrappers;

import com.seibel.lod.core.handlers.dependencyInjection.SingletonHandler;
import com.seibel.lod.core.wrapperInterfaces.config.ILodConfigWrapperSingleton;
import com.seibel.lod.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.lod.fabric.wrappers.modAccessor.ModChecker;
import com.seibel.lod.common.wrappers.config.LodConfigWrapperSingleton;

/**
 * Binds all necessary dependencies so we
 * can access them in Core. <br>
 * This needs to be called before any Core classes
 * are loaded.
 * 
 * @author James Seibel
 * @author Ran
 * @version 3-5-2022
 */
public class FabricDependencySetup
{
	public static void createInitialBindings()
	{
		SingletonHandler.bind(IModChecker.class, ModChecker.INSTANCE);
		SingletonHandler.bind(ILodConfigWrapperSingleton.class, LodConfigWrapperSingleton.INSTANCE);
	}
	
	public static void finishBinding()
	{
		SingletonHandler.finishBinding();
	}
}