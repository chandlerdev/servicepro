"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

export interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, size, totalElements, totalPages, onPageChange }: PaginationProps) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <div className="flex flex-wrap items-center gap-2 pt-3 text-[12.5px] text-muted">
      <span>
        {from}–{to} of {totalElements}
      </span>
      <div className="flex-1" />
      <button
        type="button"
        className="btn btn-sec btn-sm"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
        aria-label="Previous page"
      >
        <ChevronLeft size={14} /> Prev
      </button>
      <span>
        Page {page + 1} / {Math.max(1, totalPages)}
      </span>
      <button
        type="button"
        className="btn btn-sec btn-sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        aria-label="Next page"
      >
        Next <ChevronRight size={14} />
      </button>
    </div>
  );
}
