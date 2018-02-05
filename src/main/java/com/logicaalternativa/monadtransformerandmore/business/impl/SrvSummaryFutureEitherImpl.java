package com.logicaalternativa.monadtransformerandmore.business.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import scala.Tuple2;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.util.Either;
import scala.util.Right;
import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;

import com.logicaalternativa.monadtransformerandmore.bean.Author;
import com.logicaalternativa.monadtransformerandmore.bean.Book;
import com.logicaalternativa.monadtransformerandmore.bean.Chapter;
import com.logicaalternativa.monadtransformerandmore.bean.Sales;
import com.logicaalternativa.monadtransformerandmore.bean.Summary;
import com.logicaalternativa.monadtransformerandmore.business.SrvSummaryFutureEither;
import com.logicaalternativa.monadtransformerandmore.errors.Error;
import com.logicaalternativa.monadtransformerandmore.monad.MonadFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceAuthorFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceBookFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceChapterFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceSalesFutEither;
import com.logicaalternativa.monadtransformerandmore.util.Java8;

import static com.logicaalternativa.monadtransformerandmore.util.Java8.*;
import static com.logicaalternativa.monadtransformerandmore.util.TDD.$_notYetImpl;

public class SrvSummaryFutureEitherImpl implements SrvSummaryFutureEither<Error> {

	private final ServiceBookFutEither<Error> srvBook;
	private final ServiceSalesFutEither<Error> srvSales;
	private final ServiceChapterFutEither<Error> srvChapter;
	private final ServiceAuthorFutEither<Error> srvAuthor;
	
	private final MonadFutEither<Error> m;
	
	final ExecutionContextExecutor ec = ExecutionContexts.global();
	
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
		
		Future<Either<Error, Book>> bookFut = srvBook.getBook( idBook );
		
		Future<Either<Error, Sales>> salesFut = srvSales.getSales(idBook);
		
//		bookFut.flatMap(
//				
//				mapperF( bookE -> salesFut )				
//				
//				,ec);
//		
//			bookFut.flatMap(
//				
//				mapperF( bookE -> srvSales.getSales(idBook) )				
//				
//				,ec);		
		
		
		final Future<Tuple2<Either<Error, Book>, Either<Error, Sales>>> zip = bookFut.zip( salesFut );
		
		
		final Future<Either<Error, Summary>> res = zip.flatMap(
				
				mapperF(
						tuple -> {
					
						Either<Error, Book> bookE = tuple._1();
						Either<Error, Sales> salesE = tuple._2();
						
						final Book book = bookE.right().get();
						final Sales sales = salesE.right().get();
						
						String idAuthor = book.getIdAuthor();						
						
						Future<Either<Error, Summary>> summaryF = srvAuthor.getAuthor(idAuthor).map(								
								mapperF( 
										authorE -> {
											
											final Author author = authorE.right().get();
											
											List<Chapter> chapter = null;
											Summary summary = new Summary(book, chapter , Optional.of(sales), author);											
											
											return new Right<>(summary);
											
										}  							
										
								),
								ec );					
						
						
						return summaryF;
					}
				
				)
				,ec);		
		
		return res;
	}

}
