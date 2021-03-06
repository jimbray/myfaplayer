/*
 *     Generated by class-dump 3.1.1.
 *
 *     class-dump is Copyright (C) 1997-1998, 2000-2001, 2004-2006 by Steve Nygard.
 */

#import <BackRow/BRBaseParserDelegate.h>

@class BRMusicStoreCollection, BRRSSMediaAsset, NSAutoreleasePool;

@interface BRMusicStoreDelegate : BRBaseParserDelegate
{
    id <BRMediaProvider> _provider;
    BRRSSMediaAsset *_mediaAsset;
    BRMusicStoreCollection *_collection;
    unsigned int _inCollection:1;
    NSAutoreleasePool *_pool;
}

- (id)initWithMediaProvider:(id)fp8 collection:(id)fp12;
- (void)dealloc;
- (void)startEntryWithAttributes:(id)fp8;
- (void)startLinkWithAttributes:(id)fp8;
- (void)startCategoryWithAttributes:(id)fp8;
- (void)startCollectionWithAttributes:(id)fp8;
- (void)endCollection;
- (void)endId;
- (void)endName;
- (void)endArtist;
- (void)endImage;
- (void)endReleaseDate;
- (void)endEntry;
- (void)endRights;
- (void)endSummary;
- (void)endDuration;

@end

