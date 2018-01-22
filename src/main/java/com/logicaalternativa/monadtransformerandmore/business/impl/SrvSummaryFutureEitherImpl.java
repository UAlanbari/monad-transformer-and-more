package com.logicaalternativa.monadtransformerandmore.business.impl;

import akka.dispatch.ExecutionContexts;
import com.logicaalternativa.monadtransformerandmore.bean.*;
import com.logicaalternativa.monadtransformerandmore.business.SrvSummaryFutureEither;
import com.logicaalternativa.monadtransformerandmore.errors.Error;
import com.logicaalternativa.monadtransformerandmore.monad.MonadFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceAuthorFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceBookFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceChapterFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceSalesFutEither;
import com.logicaalternativa.monadtransformerandmore.util.Java8;
import scala.Tuple2;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.util.Either;
import scala.util.Right;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static akka.dispatch.Futures.sequence;

public class SrvSummaryFutureEitherImpl implements SrvSummaryFutureEither<Error> {

	private final ServiceBookFutEither<Error> srvBook;
	private final ServiceSalesFutEither<Error> srvSales;
	private final ServiceChapterFutEither<Error> srvChapter;
	private final ServiceAuthorFutEither<Error> srvAuthor;

	private final MonadFutEither<Error> m;


	public SrvSummaryFutureEitherImpl(ServiceBookFutEither<Error> srvBook,
									  ServiceSalesFutEither<Error> srvSales,
									  ServiceChapterFutEither<Error> srvChapter,
									  ServiceAuthorFutEither<Error> srvAuthor,
									  MonadFutEither<Error> m) {
		super();
		this.srvBook = srvBook;
		this.srvSales = srvSales;
		this.srvChapter = srvChapter;
		this.srvAuthor = srvAuthor;
		this.m= m;
	}

	@Override
	public Future<Either<Error, Summary>> getSummary(Integer idBook) {

		Future<Either<Error, Book>> bookFuture = srvBook.getBook(idBook);

		Future<Either<Error, Sales>> salesFuture = srvSales.getSales(idBook);

		Future<Tuple2<Either<Error, Book>, Either<Error, Sales>>> ziper = bookFuture.zip( salesFuture);

		ExecutionContextExecutor ec = ExecutionContexts.global();

		Future<Either<Error, Summary>> result = getEitherSummaryFuture(ziper, ec);

		return result;
	}

	private Future<Either<Error, Summary>> getEitherSummaryFuture(Future<Tuple2<Either<Error, Book>, Either<Error, Sales>>> ziper, ExecutionContextExecutor ec) {
		return ziper.flatMap(Java8.mapperF(
				eitherTupla -> {

					final Book book = eitherTupla._1.right().get();

					final Optional<Sales> sales = (eitherTupla._2.isRight() ? (Optional.of(eitherTupla._2.right().get())) : Optional.empty());
					Future<Either<Error, Author>> authorFuture = srvAuthor.getAuthor(book.getIdAuthor());
					Future<Either<Error, Summary>> res = getEitherAuthorFuture(ec, book, sales, authorFuture);

					return res;
				}
		), ec);
	}

	private Future<Either<Error, Summary>> getEitherAuthorFuture(ExecutionContextExecutor ec, Book book, Optional<Sales> sales, Future<Either<Error, Author>> authorFuture) {
		return authorFuture.flatMap(Java8.mapperF(
				(eitherAuthor) ->{
					final Author author = eitherAuthor.right().get();
					final Future<Iterable<Either<Error, Chapter>>> futureListOfInts = sequence(getListChapters(book.getChapters()), ec);
					final Future<Either<Error, Summary>> chapterFuture = getEitherChapterFuture(ec, book, sales, author, futureListOfInts);
					return chapterFuture;
				}
		), ec);
	}

	private Future<Either<Error, Summary>> getEitherChapterFuture(ExecutionContextExecutor ec, Book book, Optional<Sales> sales, Author author, Future<Iterable<Either<Error, Chapter>>> futureListOfInts) {
		return futureListOfInts.map(Java8.mapperF(
				eitherChapter -> {
					List<Chapter> listChapters = new ArrayList<>();
					eitherChapter.forEach(chapter -> {
						listChapters.add(chapter.right().get());
					});
					Summary summary = new Summary(book, listChapters, sales,author);
					return new Right<Error, Summary>(summary);
				}
		), ec);
	}


	private List<Future<Either<Error, Chapter>>> getListChapters(List<Long> chapters) {
		int lengthChapters = chapters.size();
		List<Future<Either<Error, Chapter>>> chaptersFuture = new ArrayList<>();
		for (int i=0; i < lengthChapters; i++) {
			long item = chapters.get(i);
			Future<Either<Error, Chapter>> chapterFuture = srvChapter.getChapter(item);
			chaptersFuture.add(chapterFuture);
		}

		return chaptersFuture;
	}

}
