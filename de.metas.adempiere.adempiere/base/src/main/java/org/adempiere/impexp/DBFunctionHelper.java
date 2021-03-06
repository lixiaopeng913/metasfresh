/**
 * 
 */
package org.adempiere.impexp;

import org.adempiere.ad.trx.api.ITrx;
import org.compiere.util.DB;
import org.slf4j.Logger;

import de.metas.logging.LogManager;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2019 metas GmbH
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

/**
 * @author metas-dev <dev@metasfresh.com>
 *
 */
@UtilityClass
public class DBFunctionHelper
{
	private static final transient Logger log = LogManager.getLogger(DBFunctionHelper.class);

	final public void doDBFunctionCall(@NonNull final DBFunction function, final int dataImportId, final int recordId)
	{
		final String sql = new StringBuilder()
				.append("SELECT ")
				.append(function.getSchema())
				.append(".")
				.append(function.getName())
				.append("(?,?)")
				.toString();

		DB.executeFunctionCallEx(ITrx.TRXNAME_ThreadInherited, sql, new Object[] { dataImportId, recordId });
		log.info("\nCalling {}", function);
	}
}
