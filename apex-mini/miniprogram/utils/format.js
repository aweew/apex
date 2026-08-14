"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.numberText = numberText;
exports.percentText = percentText;
exports.changeClass = changeClass;
function numberText(value, digits = 2) {
    return value === null || value === undefined ? '--' : Number(value).toFixed(digits);
}
function percentText(value) {
    return value === null || value === undefined ? '--' : `${value > 0 ? '+' : ''}${numberText(value)}%`;
}
function changeClass(value) {
    return Number(value) < 0 ? 'down' : 'up';
}
