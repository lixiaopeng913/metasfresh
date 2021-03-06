package de.metas.marketing.base.process;

import org.adempiere.ad.dao.ConstantQueryFilter;
import org.adempiere.ad.dao.IQueryFilter;
import org.compiere.Adempiere;
import org.compiere.model.I_AD_User;

import de.metas.marketing.base.model.CampaignId;
import de.metas.marketing.base.model.I_MKTG_Campaign;
import de.metas.process.JavaProcess;
import de.metas.process.Param;

/*
 * #%L
 * de.metas.marketing
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

public class MKTG_ContactPerson_CreateFrom_AD_User extends JavaProcess
{

	@Param(mandatory = true, parameterName = I_MKTG_Campaign.COLUMNNAME_MKTG_Campaign_ID)
	private int campaignRecordId;

	@Override
	protected String doIt() throws Exception
	{
		// note: if the queryFilter is empty, then do not return everything
		final IQueryFilter<I_AD_User> currentSelectionFilter = getProcessInfo().getQueryFilterOrElse(ConstantQueryFilter.of(false));

		final CampaignId campaignId = CampaignId.ofRepoId(campaignRecordId);

		final MKTG_ContactPerson_ProcessBase contactPersonProcessBase = Adempiere.getBean(MKTG_ContactPerson_ProcessBase.class);
		contactPersonProcessBase.createContactPersonsForUser(currentSelectionFilter, campaignId, null);

		return MSG_OK;
	}
}
