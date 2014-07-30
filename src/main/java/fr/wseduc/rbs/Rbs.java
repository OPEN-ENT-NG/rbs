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
	
	@Override
	public void start() {
		super.start();
		
		SqlConf confType = SqlConfs.createConf(ResourceTypeController.class.getName());
		confType.setTable("resource_type");
		confType.setSchema(getSchema());
		confType.setResourceIdLabel("id");
		ResourceTypeController typeController = new ResourceTypeController();
		typeController.setCrudService(new SqlCrudService(getSchema(), "resource_type"));
		typeController.setShareService(new SqlShareService(getSchema(),"resource_type_shares",
				getEventBus(vertx), securedActions, null));
		addController(typeController);
		
		SqlConf confResource = SqlConfs.createConf(ResourceController.class.getName());
		confResource.setTable("resource");
		confResource.setSchema(getSchema());
		confResource.setResourceIdLabel("id");
		ResourceController resourceController = new ResourceController();
		resourceController.setCrudService(new SqlCrudService(getSchema(), "resource"));
		resourceController.setShareService(new SqlShareService(getSchema(),"resource_shares",
				getEventBus(vertx), securedActions, null));
		addController(resourceController);
		
		setDefaultResourceFilter(new ShareAndOwner());
	}

}