export function calculatePortfolioTotals(holdings) {
  const totalInvested = holdings.reduce((sum, item) => sum + Number(item.investedAmount ?? 0), 0);
  const redemptionProceeds = holdings.reduce((sum, item) => sum + Number(item.redemptionProceeds ?? 0), 0);
  const dividends = holdings.reduce((sum, item) => sum + Number(item.dividends ?? 0), 0);
  const currentValue = holdings.some((item) => item.currentValue == null)
    ? null
    : holdings.reduce((sum, item) => sum + Number(item.currentValue), 0);
  const gainLoss = currentValue == null
    ? null
    : currentValue + redemptionProceeds + dividends - totalInvested;
  return {
    totalInvested,
    currentValue,
    redemptionProceeds,
    dividends,
    gainLoss,
    returnPercent: gainLoss == null || totalInvested === 0
      ? null
      : (gainLoss / totalInvested) * 100,
  };
}
