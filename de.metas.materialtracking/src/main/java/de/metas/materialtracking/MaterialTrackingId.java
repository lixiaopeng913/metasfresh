package de.metas.materialtracking;

import lombok.Value;

import org.adempiere.util.lang.impl.TableRecordReference;

import de.metas.materialtracking.model.I_M_Material_Tracking;
import de.metas.util.Check;
import de.metas.util.lang.RepoIdAware;

/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Value
public class MaterialTrackingId implements RepoIdAware
{
	int repoId;

	public static MaterialTrackingId ofRepoId(final int repoId)
	{
		return new MaterialTrackingId(repoId);
	}

	public static MaterialTrackingId ofRepoIdOrNull(final int repoId)
	{
		return repoId > 0 ? new MaterialTrackingId(repoId) : null;
	}

	private MaterialTrackingId(final int repoId)
	{
		this.repoId = Check.assumeGreaterThanZero(repoId, "productId");
	}

	public TableRecordReference toTableRecordReference()
	{
		return TableRecordReference.of(I_M_Material_Tracking.Table_Name, getRepoId());
	}

	public static int toRepoId(final MaterialTrackingId materialTrackingId)
	{
		return materialTrackingId != null ? materialTrackingId.getRepoId() : -1;
	}
}
