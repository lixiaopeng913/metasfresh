package de.metas.costing.methods;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.adempiere.exceptions.AdempiereException;
import org.eevolution.api.CostCollectorType;
import org.eevolution.api.IPPCostCollectorBL;
import org.eevolution.api.IPPOrderCostBL;
import org.eevolution.api.PPOrderCosts;
import org.eevolution.model.I_PP_Cost_Collector;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableSet;

import de.metas.acct.api.AcctSchemaId;
import de.metas.acct.api.IAcctSchemaDAO;
import de.metas.costing.CostAmount;
import de.metas.costing.CostDetailCreateRequest;
import de.metas.costing.CostDetailCreateResult;
import de.metas.costing.CostDetailVoidRequest;
import de.metas.costing.CostPrice;
import de.metas.costing.CostSegment;
import de.metas.costing.CostSegmentAndElement;
import de.metas.costing.CostingDocumentRef;
import de.metas.costing.CostingMethod;
import de.metas.costing.CurrentCost;
import de.metas.currency.CurrencyPrecision;
import de.metas.material.planning.IResourceProductService;
import de.metas.material.planning.pporder.PPOrderBOMLineId;
import de.metas.material.planning.pporder.PPOrderId;
import de.metas.order.OrderLineId;
import de.metas.product.ProductId;
import de.metas.product.ResourceId;
import de.metas.util.Services;
import lombok.NonNull;

/*
 * #%L
 * de.metas.adempiere.libero.libero
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

@Component
public class ManufacturingAveragePOCostingMethodHandler implements CostingMethodHandler
{
	// services
	private final IPPCostCollectorBL costCollectorsService = Services.get(IPPCostCollectorBL.class);
	private final IResourceProductService resourceProductService = Services.get(IResourceProductService.class);
	private final IPPOrderCostBL ppOrderCostsService = Services.get(IPPOrderCostBL.class);
	private final IAcctSchemaDAO acctSchemasRepo = Services.get(IAcctSchemaDAO.class);
	//
	private final CostingMethodHandlerUtils utils;

	private static final ImmutableSet<String> HANDLED_TABLE_NAMES = ImmutableSet.<String> builder()
			.add(CostingDocumentRef.TABLE_NAME_PP_Cost_Collector)
			.build();

	public ManufacturingAveragePOCostingMethodHandler(
			@NonNull final CostingMethodHandlerUtils utils)
	{
		this.utils = utils;
	}

	@Override
	public CostingMethod getCostingMethod()
	{
		return CostingMethod.AveragePO;
	}

	@Override
	public Set<String> getHandledTableNames()
	{
		return HANDLED_TABLE_NAMES;
	}

	@Override
	public Optional<CostDetailCreateResult> createOrUpdateCost(final CostDetailCreateRequest request)
	{
		final I_PP_Cost_Collector cc = costCollectorsService.getById(request.getDocumentRef().getRecordId());
		final CostCollectorType costCollectorType = CostCollectorType.ofCode(cc.getCostCollectorType());
		final PPOrderId orderId = PPOrderId.ofRepoId(cc.getPP_Order_ID());
		final PPOrderBOMLineId orderBOMLineId = PPOrderBOMLineId.ofRepoIdOrNull(cc.getPP_Order_BOMLine_ID());

		final PPOrderCosts orderCosts;
		final CurrentCost currentCost;

		final CostDetailCreateResult result;
		if (costCollectorType.isMaterialReceiptOrCoProduct())
		{
			orderCosts = ppOrderCostsService.getByOrderId(orderId);
			currentCost = utils.getCurrentCost(request);
			result = createMainProductOrCoProductReceipt(request, currentCost, orderCosts);
		}
		else if (costCollectorType.isAnyComponentIssue(orderBOMLineId))
		{
			orderCosts = ppOrderCostsService.getByOrderId(orderId);
			currentCost = utils.getCurrentCost(request);
			result = createComponentIssue(request, currentCost, orderCosts);
		}
		else if (costCollectorType.isActivityControl())
		{
			final ResourceId actualResourceId = ResourceId.ofRepoId(cc.getS_Resource_ID());
			final ProductId actualResourceProductId = resourceProductService.getProductIdByResourceId(actualResourceId);
			final Duration totalDuration = costCollectorsService.getTotalDurationReported(cc);

			orderCosts = null;
			currentCost = null;
			result = createActivityControl(request.withProductId(actualResourceProductId), totalDuration);
		}
		else if (costCollectorType.isUsageVariance()
				|| costCollectorType.isMethodChangeVariance()
				|| costCollectorType.isRateVariance())
		{
			// those cost collectors are specific to standard costs,
			// so we are ignoring them
			orderCosts = null;
			currentCost = null;
			result = null;
		}
		else
		{
			orderCosts = null;
			currentCost = null;
			result = null;
		}

		//
		if (orderCosts != null)
		{
			orderCosts.updatePostCalculationAmountsForCostElement(getCostingPrecision(request), request.getCostElementId());
			ppOrderCostsService.save(orderCosts);
		}

		//
		if (currentCost != null)
		{
			utils.saveCurrentCost(currentCost);
		}

		return Optional.ofNullable(result);
	}

	private CurrencyPrecision getCostingPrecision(final CostDetailCreateRequest request)
	{
		final AcctSchemaId acctSchemaId = request.getAcctSchemaId();
		return acctSchemasRepo.getById(acctSchemaId)
				.getCosting()
				.getCostingPrecision();
	}

	private CostDetailCreateResult createMainProductOrCoProductReceipt(
			@NonNull final CostDetailCreateRequest request,
			@NonNull final CurrentCost currentCost,
			@NonNull final PPOrderCosts orderCosts)
	{
		final CostSegmentAndElement costSegmentAndElement = utils.extractCostSegmentAndElement(request);

		final CostDetailCreateRequest requestEffective;
		if (!request.isReversal())
		{
			final CostPrice price = orderCosts.getPriceByCostSegmentAndElement(costSegmentAndElement)
					.orElseThrow(() -> new AdempiereException("No cost price found for " + costSegmentAndElement + " in " + orderCosts));

			final CostAmount amt = price.multiply(request.getQty()).roundToPrecisionIfNeeded(currentCost.getPrecision());

			requestEffective = request.withAmount(amt);
		}
		else
		{
			requestEffective = request;
		}

		final CostDetailCreateResult result = utils.createCostDetailRecordWithChangedCosts(requestEffective, currentCost);
		currentCost.addWeightedAverage(requestEffective.getAmt(), requestEffective.getQty());

		// Accumulate to order costs
		// NOTE: outbound amounts are negative, so we have to negate it here in order to get a positive value
		orderCosts.accumulateOutboundCostAmount(costSegmentAndElement, requestEffective.getAmt().negate(), requestEffective.getQty().negate());

		return result;
	}

	private CostDetailCreateResult createComponentIssue(
			@NonNull final CostDetailCreateRequest request,
			@NonNull final CurrentCost currentCosts,
			@NonNull final PPOrderCosts orderCosts)
	{
		final CostDetailCreateResult result;
		if (request.isReversal())
		{
			result = utils.createCostDetailRecordWithChangedCosts(request, currentCosts);
			currentCosts.addWeightedAverage(request.getAmt(), request.getQty());
		}
		else
		{
			final CostPrice price = currentCosts.getCostPrice();
			final CostAmount amt = price.multiply(request.getQty()).roundToPrecisionIfNeeded(currentCosts.getPrecision());
			final CostDetailCreateRequest requestEffective = request.withAmount(amt);
			result = utils.createCostDetailRecordWithChangedCosts(requestEffective, currentCosts);

			currentCosts.addToCurrentQtyAndCumulate(requestEffective.getQty(), requestEffective.getAmt());
		}

		// Accumulate to order costs
		final CostSegmentAndElement costSegmentAndElement = utils.extractCostSegmentAndElement(request);
		orderCosts.accumulateInboundCostAmount(costSegmentAndElement, request.getAmt(), request.getQty());

		return result;
	}

	private CostDetailCreateResult createActivityControl(final CostDetailCreateRequest request, final Duration totalDuration)
	{
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public void voidCosts(final CostDetailVoidRequest request)
	{
		// TODO
		throw new UnsupportedOperationException();
	}

	@Override
	public Optional<CostAmount> calculateSeedCosts(final CostSegment costSegment, final OrderLineId orderLineId)
	{
		return Optional.empty();
	}
}
