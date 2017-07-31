/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs;

import net.atos.entng.rbs.controllers.BookingController;
import net.atos.entng.rbs.controllers.DisplayController;
import net.atos.entng.rbs.controllers.ResourceController;
import net.atos.entng.rbs.controllers.ResourceTypeController;
import net.atos.entng.rbs.events.RbsRepositoryEvents;
import net.atos.entng.rbs.events.RbsSearchingEvents;
import net.atos.entng.rbs.filters.TypeOwnerSharedOrLocalAdmin;
import net.atos.entng.rbs.service.IcalExportService;
import net.atos.entng.rbs.service.pdf.PdfExportService;
import org.entcore.common.http.BaseServer;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.share.impl.SqlShareService;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonArray;

public class Rbs extends BaseServer {

	public final static String RBS_NAME = "RBS";
	public final static String BOOKING_TABLE = "booking";
	public final static String RESOURCE_TABLE = "resource";
	public final static String RESOURCE_SHARE_TABLE = "resource_shares";
	public final static String RESOURCE_TYPE_TABLE = "resource_type";
	public final static String RESOURCE_TYPE_SHARE_TABLE = "resource_type_shares";

	@Override
	public void start() {
		super.start();
		final EventBus eb = getEventBus(vertx);

		// Set RepositoryEvents implementation used to process events published for transition
		setRepositoryEvents(new RbsRepositoryEvents());
		if (config.getBoolean("searching-event", true)) {
			setSearchingEvents(new RbsSearchingEvents());
		}
		// Controllers
		addController(new DisplayController());

		SqlConf confType = SqlConfs.createConf(ResourceTypeController.class.getName());
		confType.setTable(RESOURCE_TYPE_TABLE);
		confType.setShareTable(RESOURCE_TYPE_SHARE_TABLE);
		confType.setSchema(getSchema());
		ResourceTypeController typeController = new ResourceTypeController(eb);
		SqlCrudService typeSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TYPE_TABLE, RESOURCE_TYPE_SHARE_TABLE,
				new JsonArray().addString("*"), new JsonArray().add("*"), true);
		typeController.setCrudService(typeSqlCrudService);
		typeController.setShareService(new SqlShareService(getSchema(), RESOURCE_TYPE_SHARE_TABLE,
				eb, securedActions, null));
		addController(typeController);

		SqlConf confResource = SqlConfs.createConf(ResourceController.class.getName());
		confResource.setTable(RESOURCE_TABLE);
		confResource.setShareTable(RESOURCE_SHARE_TABLE);
		confResource.setSchema(getSchema());
		ResourceController resourceController = new ResourceController();
		SqlCrudService resourceSqlCrudService = new SqlCrudService(getSchema(), RESOURCE_TABLE, RESOURCE_SHARE_TABLE,
				new JsonArray().addString("*"), new JsonArray().add("*"), true);
		resourceController.setCrudService(resourceSqlCrudService);
		resourceController.setShareService(new SqlShareService(getSchema(), RESOURCE_SHARE_TABLE,
				eb, securedActions, null));
		addController(resourceController);


		container.deployWorkerVerticle(PdfExportService.class.getName(), config);
		container.deployWorkerVerticle(IcalExportService.class.getName(), config);

		BookingController bookingController = new BookingController(eb);
		addController(bookingController);

		setDefaultResourceFilter(new TypeOwnerSharedOrLocalAdmin());
	}

}
