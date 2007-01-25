/**
 * Copyright (c) 2000-2006 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.service.impl;

import com.liferay.counter.model.Counter;
import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.NoSuchPermissionException;
import com.liferay.portal.NoSuchResourceException;
import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.security.permission.PermissionCheckerBag;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.OrgGroupPermission;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.Permission;
import com.liferay.portal.model.Resource;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.model.impl.ResourceImpl;
import com.liferay.portal.security.permission.PermissionCheckerImpl;
import com.liferay.portal.security.permission.ResourceActionsUtil;
import com.liferay.portal.service.PermissionLocalService;
import com.liferay.portal.service.ResourceLocalServiceUtil;
import com.liferay.portal.service.persistence.GroupUtil;
import com.liferay.portal.service.persistence.OrgGroupPermissionFinder;
import com.liferay.portal.service.persistence.OrgGroupPermissionPK;
import com.liferay.portal.service.persistence.OrgGroupPermissionUtil;
import com.liferay.portal.service.persistence.OrganizationUtil;
import com.liferay.portal.service.persistence.PermissionFinder;
import com.liferay.portal.service.persistence.PermissionUtil;
import com.liferay.portal.service.persistence.ResourceUtil;
import com.liferay.portal.service.persistence.RoleUtil;
import com.liferay.portal.service.persistence.UserGroupUtil;
import com.liferay.portal.service.persistence.UserUtil;
import com.liferay.util.Validator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <a href="PermissionLocalServiceImpl.java.html"><b><i>View Source</i></b></a>
 *
 * @author  Charles May
 * @author  Brian Wing Shun Chan
 *
 */
public class PermissionLocalServiceImpl implements PermissionLocalService {

	public Permission addPermission(
			String companyId, String actionId, long resourceId)
		throws PortalException, SystemException {

		Permission permission = PermissionUtil.fetchByA_R(actionId, resourceId);

		if (permission == null) {
			long permissionId =	CounterLocalServiceUtil.increment(
				Permission.class.getName());

			permission = PermissionUtil.create(permissionId);

			permission.setCompanyId(companyId);
			permission.setActionId(actionId);
			permission.setResourceId(resourceId);

			PermissionUtil.update(permission);
		}

		return permission;
	}

	public List addPermissions(
			String companyId, String name, long resourceId,
			boolean portletActions)
		throws PortalException, SystemException {

		List permissions = new ArrayList();

		List actions = null;

		if (portletActions) {
			actions =
				ResourceActionsUtil.getPortletResourceActions(companyId, name);
		}
		else {
			actions = ResourceActionsUtil.getModelResourceActions(name);
		}

		for (int i = 0; i < actions.size(); i++) {
			String actionId = (String)actions.get(i);

			Permission permission =
				addPermission(companyId, actionId, resourceId);

			permissions.add(permission);
		}

		return permissions;
	}

	public void addUserPermissions(
			String userId, String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		User user = UserUtil.findByPrimaryKey(userId);

		List permissions = PermissionFinder.findByU_R(userId, resourceId);

		permissions = getPermissions(
			user.getCompanyId(), actionIds, resourceId);

		UserUtil.addPermissions(userId, permissions);
	}

	public List getActions(List permissions) throws SystemException {
		List actions = new ArrayList();

		Iterator itr = permissions.iterator();

		while (itr.hasNext()) {
			Permission permission = (Permission)itr.next();

			actions.add(permission.getActionId());
		}

		return actions;
	}

	public List getGroupPermissions(long groupId, long resourceId)
		throws SystemException {

		return PermissionFinder.findByG_R(groupId, resourceId);
	}

	public List getOrgGroupPermissions(
			String organizationId, long groupId, long resourceId)
		throws SystemException {

		return PermissionFinder.findByO_G_R(
			organizationId, groupId, resourceId);
	}

	public List getPermissions(
			String companyId, String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		List permissions = new ArrayList();

		for (int i = 0; i < actionIds.length; i++) {
			Permission permission =
				addPermission(companyId, actionIds[i], resourceId);

			permissions.add(permission);
		}

		return permissions;
	}

	public List getUserPermissions(String userId, long resourceId)
		throws SystemException {

		return PermissionFinder.findByU_R(userId, resourceId);
	}

	public boolean hasGroupPermission(
			long groupId, String actionId, long resourceId)
		throws PortalException, SystemException {

		Permission permission = null;

		try {
			permission = PermissionUtil.findByA_R(actionId, resourceId);
		}
		catch (NoSuchPermissionException nspe) {

			// Return false if there is no permission based on the given action
			// id and resource id

			return false;
		}

		return GroupUtil.containsPermission(
			groupId, permission.getPermissionId());
	}

	public boolean hasRolePermission(
			String roleId, String companyId, String name, String typeId,
			String scope, String actionId)
		throws PortalException, SystemException {

		Iterator itr = ResourceUtil.findByC_N_T_S(
			companyId, name, typeId, scope).iterator();

		while (itr.hasNext()) {
			Resource resource = (Resource)itr.next();

			try {
				Permission permission = PermissionUtil.findByA_R(
					actionId, resource.getResourceId());

				if (RoleUtil.containsPermission(
						roleId, permission.getPermissionId())) {

					return true;
				}
			}
			catch (NoSuchPermissionException nspe) {
			}
		}

		return false;
	}

	public boolean hasRolePermission(
			String roleId, String companyId, String name, String typeId,
			String scope, String primKey, String actionId)
		throws PortalException, SystemException {

		try {
			Resource resource = ResourceUtil.findByC_N_T_S_P(
				companyId, name, typeId, scope, primKey);

			Permission permission = PermissionUtil.findByA_R(
				actionId, resource.getResourceId());

			return RoleUtil.containsPermission(
				roleId, permission.getPermissionId());
		}
		catch (NoSuchPermissionException nspe) {
		}
		catch (NoSuchResourceException nsre) {
		}

		return false;
	}

	public boolean hasUserPermission(
			String userId, String actionId, long resourceId)
		throws PortalException, SystemException {

		Permission permission = null;

		try {
			permission = PermissionUtil.findByA_R(actionId, resourceId);
		}
		catch (NoSuchPermissionException nspe) {

			// Return false if there is no permission based on the given action
			// id and resource id

			return false;
		}

		return UserUtil.containsPermission(
			userId, permission.getPermissionId());
	}

	public boolean hasUserPermissions(
			String userId, long groupId, String actionId,
			long[] resourceIds, PermissionCheckerBag permissionCheckerBag)
		throws PortalException, SystemException {

		long start = 0;
		int block = 1;

		if (_log.isDebugEnabled()) {
			start = System.currentTimeMillis();
		}

		// Return false if there is no resources

		if ((Validator.isNull(actionId)) || (resourceIds == null) ||
			(resourceIds.length == 0)) {

			return false;
		}

		List permissions = PermissionFinder.findByA_R(actionId, resourceIds);

		// Return false if there are no permissions

		if (permissions.size() == 0) {
			return false;
		}

		// Record logs with the first resource id

		long resourceId = resourceIds[0];

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		List userGroups = permissionCheckerBag.getUserGroups();
		List userOrgs = permissionCheckerBag.getUserOrgs();
		//List userOrgGroups = permissionCheckerBag.getUserOrgGroups();
		//List userUserGroupGroups =
		//	permissionCheckerBag.getUserUserGroupGroups();
		List groups = permissionCheckerBag.getGroups();
		List roles = permissionCheckerBag.getRoles();

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Check the organization and community intersection table. Break out of
		// this method if the user has one of the permissions set at the
		// intersection because that takes priority.

		if (checkOrgGroupPermission(userOrgs, userGroups, permissions)) {
			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		if (PermissionCheckerImpl.USER_CHECK_ALGORITHM == 1) {
			return hasUserPermissions_1(
				userId, actionId, resourceId, permissions, groups, groupId,
				start, block);
		}
		else if (PermissionCheckerImpl.USER_CHECK_ALGORITHM == 2) {
			return hasUserPermissions_2(
				userId, actionId, resourceId, permissions, groups, groupId,
				start, block);
		}
		else if (PermissionCheckerImpl.USER_CHECK_ALGORITHM == 3) {
			return hasUserPermissions_3(
				userId, actionId, resourceId, permissions, groups, roles, start,
				block);
		}
		else if (PermissionCheckerImpl.USER_CHECK_ALGORITHM == 4) {
			return hasUserPermissions_4(
				userId, actionId, resourceId, permissions, groups, roles, start,
				block);
		}

		return false;
	}

	public void setGroupPermissions(
			long groupId, String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		Group group = GroupUtil.findByPrimaryKey(groupId);

		Iterator itr = PermissionFinder.findByG_R(
			groupId, resourceId).iterator();

		while (itr.hasNext()) {
			Permission permission = (Permission)itr.next();

			GroupUtil.removePermission(groupId, permission);
		}

		List permissions = getPermissions(
			group.getCompanyId(), actionIds, resourceId);

		GroupUtil.addPermissions(groupId, permissions);
	}

	public void setGroupPermissions(
			String className, String classPK, long groupId,
			String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		long associatedGroupId = 0;

		if (className.equals(Organization.class.getName())) {
			Organization organization =
				OrganizationUtil.findByPrimaryKey(classPK);

			OrgGroupPermissionFinder.removeByO_G_R(
				classPK, groupId, resourceId);

			associatedGroupId = organization.getGroup().getGroupId();
		}
		else if (className.equals(UserGroup.class.getName())) {
			UserGroup userGroup = UserGroupUtil.findByPrimaryKey(classPK);

			associatedGroupId = userGroup.getGroup().getGroupId();
		}

		setGroupPermissions(associatedGroupId, actionIds, resourceId);
	}

	public void setOrgGroupPermissions(
			String organizationId, long groupId, String[] actionIds,
			long resourceId)
		throws PortalException, SystemException {

		Organization organization =
			OrganizationUtil.findByPrimaryKey(organizationId);

		long orgGroupId = organization.getGroup().getGroupId();

		Iterator itr = PermissionUtil.findByResourceId(resourceId).iterator();

		while (itr.hasNext()) {
			Permission permission = (Permission)itr.next();

			GroupUtil.removePermission(orgGroupId, permission);
		}

		itr = getPermissions(
			organization.getCompanyId(), actionIds, resourceId).iterator();

		OrgGroupPermissionFinder.removeByO_G_R(
			organizationId, groupId, resourceId);

		while (itr.hasNext()) {
			Permission permission = (Permission)itr.next();

			OrgGroupPermissionPK pk = new OrgGroupPermissionPK(
				organizationId, groupId, permission.getPermissionId());

			OrgGroupPermission orgGroupPermission =
				OrgGroupPermissionUtil.create(pk);

			OrgGroupPermissionUtil.update(orgGroupPermission);
		}
	}

	public void setRolePermission(
			String roleId, String companyId, String name, String typeId,
			String scope, String primKey, String actionId)
		throws PortalException, SystemException {

		if (scope.equals(ResourceImpl.SCOPE_COMPANY)) {

			// Remove group permission

			unsetRolePermissions(
				roleId, companyId, name, typeId, ResourceImpl.SCOPE_GROUP,
				actionId);
		}
		else if (scope.equals(ResourceImpl.SCOPE_GROUP)) {

			// Remove company permission

			unsetRolePermissions(
				roleId, companyId, name, typeId, ResourceImpl.SCOPE_COMPANY,
				actionId);
		}
		else if (scope.equals(ResourceImpl.SCOPE_INDIVIDUAL)) {
			throw new NoSuchPermissionException();
		}

		Resource resource = ResourceLocalServiceUtil.addResource(
			companyId, name, typeId, scope, primKey);

		Permission permission = null;

		try {
			permission = PermissionUtil.findByA_R(
				actionId, resource.getResourceId());
		}
		catch (NoSuchPermissionException nspe) {
			long permissionId =	CounterLocalServiceUtil.increment(
				Counter.class.getName());

			permission = PermissionUtil.create(permissionId);

			permission.setCompanyId(companyId);
			permission.setActionId(actionId);
			permission.setResourceId(resource.getResourceId());

			PermissionUtil.update(permission);
		}

		RoleUtil.addPermission(roleId, permission);
	}

	public void setUserPermissions(
			String userId, String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		User user = UserUtil.findByPrimaryKey(userId);

		List permissions = PermissionFinder.findByU_R(userId, resourceId);

		UserUtil.removePermissions(userId, permissions);

		permissions = getPermissions(
			user.getCompanyId(), actionIds, resourceId);

		UserUtil.addPermissions(userId, permissions);
	}

	public void unsetRolePermission(
			String roleId, String companyId, String name, String typeId,
			String scope, String primKey, String actionId)
		throws PortalException, SystemException {

		try {
			Resource resource = ResourceUtil.findByC_N_T_S_P(
				companyId, name, typeId, scope, primKey);

			Permission permission = PermissionUtil.findByA_R(
				actionId, resource.getResourceId());

			RoleUtil.removePermission(roleId, permission);
		}
		catch (NoSuchPermissionException nspe) {
		}
		catch (NoSuchResourceException nsre) {
		}
	}

	public void unsetRolePermissions(
			String roleId, String companyId, String name, String typeId,
			String scope, String actionId)
		throws PortalException, SystemException {

		Iterator itr = ResourceUtil.findByC_N_T_S(
			companyId, name, typeId, scope).iterator();

		while (itr.hasNext()) {
			Resource resource = (Resource)itr.next();

			try {
				Permission permission = PermissionUtil.findByA_R(
					actionId, resource.getResourceId());

				RoleUtil.removePermission(roleId, permission);
			}
			catch (NoSuchPermissionException nspe) {
			}
		}
	}

	public void unsetUserPermissions(
			String userId, String[] actionIds, long resourceId)
		throws PortalException, SystemException {

		List permissions = PermissionFinder.findByU_A_R(
			userId, actionIds, resourceId);

		UserUtil.removePermissions(userId, permissions);
	}

	public void updateResourceId(long oldResourceId, long newResourceId)
		throws PortalException, SystemException {

		List permissions = PermissionUtil.findByResourceId(oldResourceId);

		for (int i = 0; i < permissions.size(); i++) {
			Permission permission = (Permission)permissions.get(i);

			permission.setResourceId(newResourceId);

			PermissionUtil.update(permission);
		}
	}

	protected boolean checkOrgGroupPermission(
			List organizations, List groups, List permissions)
		throws PortalException, SystemException {

		for (int i = 0; i < permissions.size(); i++) {
			Permission permission = (Permission)permissions.get(i);

			if (checkOrgGroupPermission(organizations, groups, permission)) {
				return true;
			}
		}

		return false;
	}

	protected boolean checkOrgGroupPermission(
			List organizations, List groups, Permission permission)
		throws PortalException, SystemException {

		// Do not check for an OrgGroupPermission intersection unless there is
		// at least one organization and one group to check

		if ((organizations.size() == 0) || (groups.size() == 0)) {
			return false;
		}

		// Do not check unless the OrgGroupPermission intersection contains at
		// least one permission

		List orgGroupPermissions = OrgGroupPermissionUtil.findByPermissionId(
			permission.getPermissionId());

		if (orgGroupPermissions.size() == 0) {
			return false;
		}

		Iterator itr = orgGroupPermissions.iterator();

		while (itr.hasNext()) {
			OrgGroupPermission orgGroupPermission =
				(OrgGroupPermission)itr.next();

			if (orgGroupPermission.containsOrganization(organizations) &&
				orgGroupPermission.containsGroup(groups)) {

				return true;
			}
		}

		// Throw an exception so that we do not continue checking permissions.
		// The user has a specific permission given in the OrgGroupPermission
		// intersection that prohibits him from going further.

		throw new NoSuchPermissionException(
			"User has a permission in OrgGroupPermission that does not match");
	}

	protected boolean hasUserPermissions_1(
			String userId, String actionId, long resourceId, List permissions,
			List groups, long groupId, long start, int block)
		throws PortalException, SystemException {

		// Is the user connected to one of the permissions via group or
		// organization roles?

		if (groups.size() > 0) {
			if (PermissionFinder.countByGroupsRoles(permissions, groups) > 0) {
				return true;
			}
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user associated with groups or organizations that are directly
		// connected to one of the permissions?

		if (groups.size() > 0) {
			if (PermissionFinder.countByGroupsPermissions(
					permissions, groups) > 0) {

				return true;
			}
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user connected to one of the permissions via user roles?

		if (PermissionFinder.countByUsersRoles(permissions, userId) > 0) {
			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user connected to one of the permissions via user group roles?

		if (PermissionFinder.countByUserGroupRole(
				permissions, userId, groupId) > 0) {

			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user directly connected to one of the permissions?

		if (PermissionFinder.countByUsersPermissions(permissions, userId) > 0) {
			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		return false;
	}

	protected boolean hasUserPermissions_2(
			String userId, String actionId, long resourceId, List permissions,
			List groups, long groupId, long start, int block)
		throws PortalException, SystemException {

		// Call countByGroupsRoles, countByGroupsPermissions, countByUsersRoles,
		// countByUserGroupRole, and countByUsersPermissions in one method

		if (PermissionFinder.containsPermissions_2(
				permissions, userId, groups, groupId)) {

			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		return false;
	}

	protected boolean hasUserPermissions_3(
			String userId, String actionId, long resourceId, List permissions,
			List groups, List roles, long start, int block)
		throws PortalException, SystemException {

		// Is the user associated with groups or organizations that are directly
		// connected to one of the permissions?

		if (groups.size() > 0) {
			if (PermissionFinder.countByGroupsPermissions(
					permissions, groups) > 0) {

				return true;
			}
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user associated with a role that is directly connected to one
		// of the permissions?

		if (roles.size() > 0) {
			if (PermissionFinder.countByRolesPermissions(
					permissions, roles) > 0) {

				return true;
			}
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		// Is the user directly connected to one of the permissions?

		if (PermissionFinder.countByUsersPermissions(permissions, userId) > 0) {
			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		return false;
	}

	protected boolean hasUserPermissions_4(
			String userId, String actionId, long resourceId, List permissions,
			List groups, List roles, long start, int block)
		throws PortalException, SystemException {

		// Call countByGroupsPermissions, countByRolesPermissions, and
		// countByUsersPermissions in one method

		if (PermissionFinder.containsPermissions_4(
				permissions, userId, groups, roles)) {

			return true;
		}

		start = logHasUserPermissions(
			userId, actionId, resourceId, start, block++);

		return false;
	}

	protected long logHasUserPermissions(
		String userId, String actionId, long resourceId, long start,
		int block) {

		if (!_log.isDebugEnabled()) {
			return 0;
		}

		long end = System.currentTimeMillis();

		_log.debug(
			"Checking user permissions block " + block + " for " + userId +
				" " + actionId + " " + resourceId + " takes " + (end - start) +
					" ms");

		return end;
	}

	private static Log _log =
		LogFactory.getLog(PermissionLocalServiceImpl.class);

}