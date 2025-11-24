package com.asp.parsing.writer;

import com.asp.domain.normalized.NormalizedAccount;
import com.asp.domain.normalized.NormalizedAccountSnapshot;
import com.asp.domain.normalized.NormalizedTransaction;
import com.asp.parsing.parser.IngestionContext;
import com.asp.parsing.parser.ParserResult;

import java.util.List;

/**
 * Interface for writing parsed data to the database.
 */
public interface TransactionWriter {
    /**
     * Saves the complete parser result to the database.
     *
     * @param result the parser result
     * @param context the ingestion context
     */
    void saveParserResult(ParserResult result, IngestionContext context);

    /**
     * Saves account information.
     *
     * @param account the normalized account
     * @param context the ingestion context
     */
    void saveAccount(NormalizedAccount account, IngestionContext context);

    /**
     * Saves transactions.
     *
     * @param transactions the list of transactions
     * @param context the ingestion context
     */
    void saveTransactions(List<NormalizedTransaction> transactions, IngestionContext context);

    /**
     * Saves account snapshots.
     *
     * @param snapshots the list of snapshots
     * @param context the ingestion context
     */
    void saveSnapshots(List<NormalizedAccountSnapshot> snapshots, IngestionContext context);
}

