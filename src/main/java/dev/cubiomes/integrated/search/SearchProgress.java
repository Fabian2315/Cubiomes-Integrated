package dev.cubiomes.integrated.search;

public record SearchProgress(
    long scanned,
    long stage1Passed,
    long accepted
) {
}
