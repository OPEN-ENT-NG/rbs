package fr.wseduc.rbs;

import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.sql.ShareAndOwner;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;

import fr.wseduc.rbs.controllers.ResourceController;
import fr.wseduc.rbs.controllers.ResourceTypeController;

public class Rbs extends BaseServer {
	
	public final static String RESOURCE_TABLE="resource";
	public final static String RESOURCE_SHARE_TABLE="resource_shares";
	public final static String RESOURCE_TYPE_TABLE="resource_type";
	public final static String RESOURCE_TYPE_SHARE_TABLE="resource_type_shares";
	
	@Override
	public void start() {
		super.start();
		
		SqlConf confType = SqlConfs.createConf(ResourceTypeController.class.getName());
		confType.setTable(RESOURCE_TYPE_TABLE);
		confType.setShareTable(RESOURCE_TYPE_SHARE_TABLE);
		confType.setSchema(getSchema());
		confType.setResourceIdLabel("id");
		ResourceTypeController typeController = new ResourceTypeController();
		typeController.setCrudService(new SqlCrudService(getSchema(), RESOURCE_TYPE_TABLE, RESOURCE_TYPE_SHARE_TABLE));
		typeController.setShareService(new SqlShareService(getSchema(),RESOURCE_TYPE_SHARE_TABLE,
				getEventBus(vertx), securedActions, null));
		addController(typeController);
		
		SqlConf confResource = SqlConfs.createConf(ResourceController.class.getName());
		confResource.setTable(RESOURCE_TABLE);
		confResource.setShareTable(RESOURCE_SHARE_TABLE);
		confResource.setSchema(getSchema());
		confResource.setResourceIdLabel("id");
		ResourceController resourceController = new ResourceController();
		resourceController.setCrudService(new SqlCrudService(getSchema(), RESOURCE_TABLE, RESOURCE_SHARE_TABLE));
		resourceController.setShareService(new SqlShareService(getSchema(),RESOURCE_SHARE_TABLE,
				getEventBus(vertx), securedActions, null));
		addController(resourceController);
		
		setDefaultResourceFilter(new ShareAndOwner());
	}

}