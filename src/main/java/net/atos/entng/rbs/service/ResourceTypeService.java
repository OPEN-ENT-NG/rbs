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

package net.atos.entng.rbs.service;

import java.util.List;

import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import fr.wseduc.webutils.Either;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface ResourceTypeService {

	/**
	 * Get the list of resource type
	 *
	 * @param groupsAndUserIds : list of groups and id of users who are authorized
	 * @param user             : information of current user logged
	 * @param handler          : handler which contains the response
	 */
	void list(List<String> groupsAndUserIds, UserInfos user,
	                 Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the list of resource type the user has access to for one structure
	 * ADML gets all resource types from structure directly
	 * Otherwise the user is either owner of the type or it has been shared with them
	 *
	 * @param groupsAndUserIds {@link List<String>} user groups + user id
	 * @param user {@link UserInfos} user information
	 * @param structureId {@link String} id of the target structure
	 * @param handler handler
	 */
	void list(final List<String> groupsAndUserIds, final UserInfos user, final String structureId,
						   final Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the moderator list of current resource type
	 *
	 * @param typeId  : id of current resource type
	 * @param handler : handler which contains the response
	 */
	void getModeratorsIds(String typeId, Handler<Either<String, JsonArray>> handler);

	/**
	 * Override the color of current resource type's children
	 *
	 * @param typeId        : id of current resource type
	 * @param color         : color of current resource type
	 * @param eitherHandler : handler which contains the response
	 */
	void overrideColorChild(String typeId, String color, Handler<Either<String, JsonObject>> eitherHandler);

	/**
	 * Override the validation of current resource type's children
	 *
	 * @param typeId     : id of current resource type
	 * @param validation : boolean for the validation of resource type
	 * @param handler    : handler which contains the response
	 */
	void overrideValidationChild(String typeId, Boolean validation, Handler<Either<String, JsonObject>> handler);

	/**
	 * Add the notification for current user on current resource type
	 *
	 * @param id      : id of current resource type
	 * @param user    : information of current user logged
	 * @param handler : handler which contains the response
	 */
	void addNotifications(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * Remove the notification for current user on current resource type
	 *
	 * @param id      : id of current resource type
	 * @param user    : information of current user logged
	 * @param handler : handler which contains the response
	 */
	void removeNotifications(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);
}
