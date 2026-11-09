import test from "node:test";
import assert from "node:assert/strict";
import { calculatePortfolioTotals } from "./portfolioMath.js";

test("calculates portfolio gain including redemption and dividends", () => {
  const result = calculatePortfolioTotals([{
    investedAmount: 1000,
    currentValue: 800,
    redemptionProceeds: 300,
    dividends: 25,
  }]);

  assert.equal(result.gainLoss, 125);
  assert.equal(result.returnPercent, 12.5);
});

test("keeps total current value unavailable if any holding lacks a NAV", () => {
  const result = calculatePortfolioTotals([
    { investedAmount: 500, currentValue: 550, redemptionProceeds: 0, dividends: 0 },
    { investedAmount: 100, currentValue: null, redemptionProceeds: 0, dividends: 0 },
  ]);

  assert.equal(result.currentValue, null);
  assert.equal(result.gainLoss, null);
  assert.equal(result.returnPercent, null);
});
