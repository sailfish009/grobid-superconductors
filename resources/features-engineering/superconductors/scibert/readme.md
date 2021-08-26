# Scibert fine-tuning 

## Introduction

This page contains notes and information about the process of fine-tuning SciBERT for improving the results in the task of NER for materials science scientific papers. 
This process was performed following the guide [here](https://github.com/google-research/bert#pre-training-with-bert).

The data used for fine-tuning is text data from scientific articles in material science and [SuperMat](https://github.com/lfoppiano/SuperMat) for superconductors materials (in total ~21Gb, 100001987 sentences, TBA tokens). 

- Useful references: 
  - SciBERT's'[cheatsheet](https://github.com/allenai/scibert/blob/master/scripts/cheatsheet.txt).
  - [memory consumption](https://github.com/google-research/bert#out-of-memory-issues) 

### Preliminary studies

#### Vocab comparison

This comparison attempt to figure out roughly how distant the vocabulary of the fine-tuning data (`myvocab`) and the original SciBERT dataset (`scivocab`) are. 

Notes: 
 - the vocabulary used in *BERT is limited to 31k items, so is very unlikely that the two vocabularies are going to overlap 100%
 - what is the weight of each token in the vocabulary? 
 - there are 100 unused tokens that BERT allocate for "common tokens that could appear in a specific domain" ([cf](https://github.com/allenai/scibert/issues/38#issuecomment-490520991)). Could we get these most important 100 tokesn from our `myvocab`?

##### Steps:

1. Generate a new vocabulary by training the sentencepiece model 
    ``
       python 
       import sentencepiece as spm
       spm.SentencePieceTrainer.Train('--input=[...] --model_prefix=100B_9999_cased --vocab_size=31000 --character_coverage=0.9999 --model_type=bpe --input_sentence_size=100001987 --shuffle_input_sentence=true')
    ``

2. Transform the output vocabulary (``100B_9999_cased.vocab``) as described [here](https://github.com/allenai/scibert/issues/38#issuecomment-488867883). The steps are roughly: 
   1. ``mv 100B_9999_cased.vocab myvocab.sentencepiece.txt``
   2. ``cut -f1 myvocab.sentencepiece.txt  > myvocab.txt``
   3. ``sed -e 's/^\([^▁<]\)/##\1/' myvocab.txt > myvocab.tmp.txt``
   4. ``sed -e 's/^▁//' myvocab.tmp.txt > myvocab.postprocessed.txt``
   5. ``cat myvocab.postprocessed.txt scivocab.txt | gsort | guniq -d > scivocab.intersection.myvocab.txt``
   6. ``gsort myvocab.postprocessed.txt > myvocab.postprocessed.sorted.txt``
   7. ``gsort scivocab.txt > scivocab.sorted.txt``
   8. ``comm -23 myvocab.postprocessed.sorted.txt scivocab.sorted.txt > myvocab.only.txt``
   9. ``comm -13 myvocab.postprocessed.sorted.txt scivocab.sorted.txt > scivocab.only.txt``

3. Match each item from the [myvocab.only.txt](vocab/myvocab.only.txt) into the original line of [myvocab.sentencepiece.txt](vocab/myvocab.sentencepiece.txt) using the script [token-match.py](token-match.py):
   1. result considering any token [myvocab.only.txt](vocab/myvocab.only.txt) is matched and saved into [myvocab.lookedup_from_myvocab.only.txt](vocab/myvocab.lookedup_from_myvocab.only.txt)
   2. result considering only token that are not "subwords" (do not start with a `##`) is matched and saved into  [myvocab.lookedup_from_myvocab.only.only_full_words.txt](vocab/myvocab.lookedup_from_myvocab.only.only_full_words.txt)

 

##### Results

- Vocabulary length: 31000
- Vocabulary intersection (scivocab vs myvocab): 18107 (58.40%)
- Tokens only in scivocab: 12907
- Tokens only in myvocab: 12895   

Output is [here](./vocab): 

### Fine-tuning

Starting from a text file containing one paragraph per line, we performed the following operations: 

1. Split paragraphs in sentences using [BlingFire](https://github.com/Microsoft/BlingFire), a sentence splitter. 
2. Shard the obtained resulted large file, into several smaller (max 250000 sentences). See [ref](https://github.com/google-research/bert/issues/117).
    ```
    -rw-r--r-- 1 lfoppian0 tdm  50M Aug 12 10:29 SciCorpora+SuperMat.sentences.sharded00
    -rw-r--r-- 1 lfoppian0 tdm  50M Aug 12 10:29 SciCorpora+SuperMat.sentences.sharded01
     [...]
    ```
3. Create pre-training, preprocess the sentences and add masking: 
   1. short sequences (`max_sequence_lenght=128`):
      ```
       for x in ../sharded_corpus/*; do python create_pretraining_data.py    --input_file=${x}    --output_file=./pretrained_128/science+supermat.tfrecord_${x##*.}   --vocab_file=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/vocab.txt    --do_lower_case=False    --max_seq_length=128    --max_predictions_per_seq=20    --masked_lm_prob=0.15    --random_seed=12345    --dupe_factor=5; done
      ```
   2. for large sequences (`max_sequence_lenght=512` and `max_predictions_per_seq=78` (0.15*512)):
       ```
       for x in ../sharded_corpus/*; do python create_pretraining_data.py    --input_file=${x}    --output_file=./pretrained_512/science+supermat.tfrecord_${x##*.}   --vocab_file=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/vocab.txt    --do_lower_case=False    --max_seq_length=512    --max_predictions_per_seq=78    --masked_lm_prob=0.15    --random_seed=23233    --dupe_factor=5; done
       ```
      
4. Run pre-training 

## Summary results 

TBD: improve this table and re-do these experiments with a clearer description (e.g. we want to split the training into 
two steps, one with shorter sequence and one wiht longer ones) - [ref](https://github.com/google-research/bert#pre-training-tips-and-caveats)

| Name  | training steps    | Notes | Masked accuracy | Masked loss  | Next sentence accuracy | Next sentence loss |
|--------|--------- |------|---------|----|--------|--------|
| o23445 | 20       | Just a test | 0.6763816 | 1.5105013 | 0.96125 | 0.121605136 | 
| o23446 | 100000   | running with 100000 steps and 10000 of warm up steps | 0.70375 | 1.3560376 | 0.97875 | 0.060185157 |
| o23449 | 1000000  | running with 1000000 asteps and 10000 of warm up steps | 0.7238158 | 1.2188154 | 0.97625 | 0.049554683 |


## Details parameters 

TBD: integrate this in the main table 

- o23445: `--input_file=pretrained/science+supermat.tfrecord_*   --output_dir=pretraining_output    --do_train=True    --do_eval=True    --bert_config_file=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_config.json    --init_checkpoint=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_model.ckpt    --train_batch_size=32    --max_seq_length=128    --max_predictions_per_seq=20    --num_train_steps=100000    --num_warmup_steps=10000    --learning_rate=2e-5`
- o23446: `--input_file=pretrained/science+supermat.tfrecord_*   --output_dir=pretraining_output    --do_train=True    --do_eval=True    --bert_config_file=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_config.json    --init_checkpoint=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_model.ckpt    --train_batch_size=32    --max_seq_length=128    --max_predictions_per_seq=20    --num_train_steps=100000    --num_warmup_steps=10000    --learning_rate=2e-5`
- o23449: `--input_file=pretrained/science+supermat.tfrecord_*   --output_dir=pretraining_output    --do_train=True    --do_eval=True    --bert_config_file=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_config.json    --init_checkpoint=/lustre/group/tdm/Luca/delft/delft/data/embeddings/scibert_scivocab_cased/bert_model.ckpt    --train_batch_size=32    --max_seq_length=128    --max_predictions_per_seq=20    --num_train_steps=1000000    --num_warmup_steps=10000    --learning_rate=2e-5`


## Additional information


# Credits

Various people have helped with small feedback or more useful observations and ideas: 

- Pedro Ortiz pedro.ortiz@inria.fr
- arij.riabi@inria.fr
- Romain roman.castagne@inria.fr
- Patrice Lopez patrice.lopez@science-miner.com
